/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.serde.protobuf;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.GenericPlaceholder;
import io.micronaut.serde.Keys;
import io.micronaut.serde.KeysSupport;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.protobuf.annotation.ProtoField;
import io.micronaut.serde.protobuf.annotation.ProtoType;
import io.micronaut.serde.protobuf.wire.ProtoWire;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.TypeVariable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The field-number layout of a single message type, derived from {@link ProtoField} annotations on
 * the type's introspected properties.
 *
 * <p>Protobuf identifies fields by number, so both directions need a translation table: the encoder
 * goes from serde property name to field number, and the decoder goes back from field number to
 * property. Both are resolved once per type and cached for the lifetime of that type.</p>
 *
 * @since 3.2
 */
@Internal
public final class ProtoSchema {

    // ClassValue keeps the cache scoped to the raw message class, while the nested map distinguishes
    // parameterizations such as Envelope<String> and Envelope<List<Integer>>.
    private static final ClassValue<Map<Argument<?>, Object>> CACHE = new ClassValue<>() {
        @Override
        protected Map<Argument<?>, Object> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    private static final int PROTO_KEYS_INDEX = KeysSupport.indexOf(new ProtobufKeysProvider());
    private static final int RESERVED_FROM = 19000;
    private static final int RESERVED_TO = 19999;

    private final Class<?> messageType;
    private final Map<String, ProtoProperty> byName;
    private final int[] numbers;
    private final ProtoProperty[] slots;
    private final Map<Keys, @Nullable ProtoProperty[]> propertiesByKeys = new ConcurrentHashMap<>();
    private final Map<Keys, int[]> keyIndexesBySlot = new ConcurrentHashMap<>();

    private ProtoSchema(Class<?> messageType, List<ProtoProperty> properties) {
        this.messageType = messageType;
        this.byName = HashMap.newHashMap(properties.size());
        for (ProtoProperty property : properties) {
            byName.put(property.name(), property);
        }
        ProtoProperty[] sorted = properties.toArray(ProtoProperty[]::new);
        Arrays.sort(sorted, Comparator.comparingInt(ProtoProperty::number));
        this.slots = sorted;
        this.numbers = new int[sorted.length];
        for (int i = 0; i < sorted.length; i++) {
            numbers[i] = sorted[i].number();
        }
    }

    /**
     * Resolve, and cache, the schema for a message type.
     *
     * @param messageType The message type
     * @return The schema
     * @throws SerdeException If the type is not introspected, or its field numbers are invalid
     */
    public static ProtoSchema of(Class<?> messageType) throws SerdeException {
        return of(Argument.of(messageType));
    }

    /**
     * Resolve, and cache, the schema for a possibly parameterized message type.
     *
     * @param messageType The message type
     * @return The schema
     * @throws SerdeException If the type is not introspected, or its field declarations are invalid
     */
    public static ProtoSchema of(Argument<?> messageType) throws SerdeException {
        Object cached = CACHE.get(messageType.getType()).computeIfAbsent(messageType, type -> {
            try {
                return resolve(type);
            } catch (SerdeException e) {
                return e;
            }
        });
        if (cached instanceof ProtoSchema schema) {
            return schema;
        }
        throw new SerdeException(String.valueOf(((SerdeException) cached).getMessage()));
    }

    /**
     * Returns the message type this schema describes.
     *
     * @return The message type
     */
    public Class<?> messageType() {
        return messageType;
    }

    /**
     * Look up a property by its serde name.
     *
     * @param name The property name
     * @return The property, or {@code null} if this message has no such property
     */
    public @Nullable ProtoProperty byName(String name) {
        return byName.get(name);
    }

    /**
     * Find the slot holding the given field number.
     *
     * @param number The field number
     * @return The slot index, or {@code -1} if this message has no such field
     */
    public int slotOf(int number) {
        int index = Arrays.binarySearch(numbers, number);
        return index < 0 ? -1 : index;
    }

    /**
     * The property in a slot returned by {@link #slotOf(int)}.
     *
     * @param slot The slot index
     * @return The property
     */
    public ProtoProperty propertyAt(int slot) {
        return slots[slot];
    }

    /**
     * Returns the number of field slots in this schema.
     *
     * @return The number of slots
     */
    public int slotCount() {
        return slots.length;
    }

    /**
     * This schema's properties in key index order, so the encoder can turn a key index straight
     * into a field number.
     *
     * <p>Serde dispatches object properties by index into a {@link Keys} set built once per bean,
     * so this translation is computed once per key set and then reused for every message.</p>
     *
     * @param keys The key set
     * @return The properties, indexed by key index; entries are {@code null} for keys this message
     *         has no field for
     * @throws SerdeException If the protobuf key contribution is not registered
     */
    public @Nullable ProtoProperty[] byKeyIndex(Keys keys) throws SerdeException {
        @Nullable ProtoProperty[] resolved = propertiesByKeys.get(keys);
        if (resolved != null) {
            return resolved;
        }
        String[] names = keyNames(keys);
        resolved = new ProtoProperty[names.length];
        for (int i = 0; i < names.length; i++) {
            resolved[i] = byName.get(names[i]);
        }
        propertiesByKeys.put(keys, resolved);
        return resolved;
    }

    /**
     * The key index of each slot, so the decoder can turn a field number straight into a key index
     * without a second name lookup.
     *
     * @param keys The key set
     * @return The key index per slot, or {@link Keys#UNKNOWN_KEY} where the key set has no such key
     */
    public int[] keyIndexBySlot(Keys keys) {
        int[] resolved = keyIndexesBySlot.get(keys);
        if (resolved != null) {
            return resolved;
        }
        resolved = new int[slots.length];
        for (int i = 0; i < slots.length; i++) {
            resolved[i] = keys.indexOf(slots[i].name());
        }
        keyIndexesBySlot.put(keys, resolved);
        return resolved;
    }

    /**
     * The property names contributed for a key set, in key index order.
     *
     * @param keys The key set
     * @return The names
     * @throws SerdeException If the protobuf key contribution is not registered
     */
    public static String[] keyNames(Keys keys) throws SerdeException {
        if (PROTO_KEYS_INDEX < 0) {
            throw new SerdeException("The protobuf KeysProvider is not registered. "
                + "META-INF/services/io.micronaut.serde.KeysProvider is missing from the classpath.");
        }
        return (String[]) KeysSupport.get(keys, PROTO_KEYS_INDEX)[ProtobufKeysProvider.KEY_NAMES_INDEX];
    }

    private static ProtoSchema resolve(Argument<?> messageArgument) throws SerdeException {
        Class<?> messageType = messageArgument.getType();
        Map<String, Argument<?>> typeBindings = typeBindings(messageArgument);
        BeanIntrospection<?> introspection = BeanIntrospector.SHARED.findIntrospection(messageType)
            .orElseThrow(() -> new SerdeException("No introspection found for [" + messageType.getName()
                + "]. Protobuf serialization requires the type to be annotated with @Serdeable or @Introspected."));

        List<ProtoProperty> properties = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        Map<Integer, String> seen = new HashMap<>();

        for (BeanProperty<?, ?> beanProperty : introspection.getBeanProperties()) {
            AnnotationMetadata metadata = beanProperty.getAnnotationMetadata();
            if (metadata.booleanValue(SerdeConfig.class, SerdeConfig.IGNORED).orElse(false)) {
                continue;
            }
            String name = metadata.stringValue(SerdeConfig.class, SerdeConfig.PROPERTY).orElse(beanProperty.getName());
            if (!metadata.hasAnnotation(ProtoField.class)) {
                missing.add(name);
                continue;
            }
            int number = metadata.intValue(ProtoField.class).orElse(0);
            if (number < ProtoField.MIN_FIELD_NUMBER || number > ProtoField.MAX_FIELD_NUMBER) {
                throw new SerdeException("Property [" + name + "] of [" + messageType.getName() + "] declares field number "
                    + number + ", which is outside the legal range " + ProtoField.MIN_FIELD_NUMBER + ".." + ProtoField.MAX_FIELD_NUMBER + ".");
            }
            if (number >= RESERVED_FROM && number <= RESERVED_TO) {
                throw new SerdeException("Property [" + name + "] of [" + messageType.getName() + "] declares field number "
                    + number + ", which falls in the range reserved by Protocol Buffers (" + RESERVED_FROM + ".." + RESERVED_TO + ").");
            }
            String clash = seen.put(number, name);
            if (clash != null) {
                throw new SerdeException("Properties [" + clash + "] and [" + name + "] of [" + messageType.getName()
                    + "] both declare field number " + number + ". Field numbers must be unique within a message.");
            }
            ProtoType type = metadata.enumValue(ProtoField.class, "type", ProtoType.class).orElse(ProtoType.DEFAULT);
            Argument<?> argument = resolveArgument(beanProperty.asArgument(), typeBindings);
            FieldShape shape = fieldShape(argument);
            validateType(messageType, name, type, shape.valueType());
            properties.add(new ProtoProperty(
                name,
                number,
                type,
                argument,
                shape.repeated(),
                isPackable(shape.valueType()),
                shape.bytes(),
                shape.repeatedBytes(),
                isMessage(shape.valueType()),
                shape.explicitPresence(),
                wireType(type, shape.valueType()),
                ProtoWire.tag(number, ProtoWire.VARINT),
                ProtoWire.tag(number, ProtoWire.FIXED32),
                ProtoWire.tag(number, ProtoWire.FIXED64),
                ProtoWire.tag(number, ProtoWire.LENGTH_DELIMITED),
                intKind(type),
                longKind(type)
            ));
        }

        if (!missing.isEmpty()) {
            throw new SerdeException("Properties " + missing + " of [" + messageType.getName()
                + "] are missing @ProtoField. Every property of a protobuf message needs an explicit field number.");
        }
        return new ProtoSchema(messageType, properties);
    }

    private static int intKind(ProtoType type) {
        return switch (type) {
            case SINT32, SINT64 -> ProtoProperty.KIND_ZIGZAG;
            case UINT32, UINT64 -> ProtoProperty.KIND_UINT32;
            case FIXED32, SFIXED32 -> ProtoProperty.KIND_FIXED32;
            case FIXED64, SFIXED64 -> ProtoProperty.KIND_FIXED64;
            default -> ProtoProperty.KIND_INT32;
        };
    }

    private static int longKind(ProtoType type) {
        return switch (type) {
            case SINT32, SINT64 -> ProtoProperty.KIND_ZIGZAG;
            case FIXED32, SFIXED32 -> ProtoProperty.KIND_FIXED32;
            case FIXED64, SFIXED64 -> ProtoProperty.KIND_FIXED64;
            default -> ProtoProperty.KIND_INT64;
        };
    }

    private static FieldShape fieldShape(Argument<?> declaredArgument) {
        boolean explicitPresence = declaredArgument.isNullable() || isOptional(declaredArgument.getType());
        Argument<?> argument = unwrapOptional(declaredArgument);
        Class<?> type = argument.getType();
        if (type == byte[].class) {
            return new FieldShape(argument, false, true, false, explicitPresence);
        }
        if (type.isArray()) {
            Argument<?> element = Argument.of(type.getComponentType());
            return new FieldShape(element, true, false, element.getType() == byte[].class, false);
        }
        if (Iterable.class.isAssignableFrom(type)) {
            Argument<?>[] parameters = argument.getTypeParameters();
            Argument<?> element = parameters.length == 1 ? parameters[0] : Argument.OBJECT_ARGUMENT;
            return new FieldShape(element, true, false, element.getType() == byte[].class, false);
        }
        return new FieldShape(argument, false, false, false, explicitPresence);
    }

    private static boolean isOptional(Class<?> type) {
        return type == Optional.class || type == OptionalInt.class || type == OptionalLong.class || type == OptionalDouble.class;
    }

    private static Argument<?> unwrapOptional(Argument<?> argument) {
        Class<?> type = argument.getType();
        if (type == Optional.class) {
            Argument<?>[] parameters = argument.getTypeParameters();
            return parameters.length == 1 ? parameters[0] : Argument.OBJECT_ARGUMENT;
        }
        if (type == OptionalInt.class) {
            return Argument.INT;
        }
        if (type == OptionalLong.class) {
            return Argument.LONG;
        }
        if (type == OptionalDouble.class) {
            return Argument.DOUBLE;
        }
        return argument;
    }

    private static boolean isPackable(Argument<?> argument) {
        Class<?> type = argument.getType();
        if (type.isPrimitive()) {
            return type != void.class;
        }
        return type == Boolean.class || type == Character.class
            || (Number.class.isAssignableFrom(type) && type != BigDecimal.class && type != BigInteger.class);
    }

    private static boolean isMessage(Argument<?> argument) {
        Class<?> type = argument.getType();
        return !type.isEnum() && BeanIntrospector.SHARED.findIntrospection(type).isPresent();
    }

    private static int wireType(ProtoType protoType, Argument<?> argument) {
        if (protoType != ProtoType.DEFAULT) {
            return ProtoProperty.wireTypeOf(isLong(argument.getType()) ? longKind(protoType) : intKind(protoType));
        }
        Class<?> type = argument.getType();
        if (type == float.class || type == Float.class) {
            return ProtoWire.FIXED32;
        }
        if (type == double.class || type == Double.class) {
            return ProtoWire.FIXED64;
        }
        return isIntegral(type) ? ProtoWire.VARINT : ProtoWire.LENGTH_DELIMITED;
    }

    private static void validateType(Class<?> messageType,
                                     String propertyName,
                                     ProtoType protoType,
                                     Argument<?> argument) throws SerdeException {
        if (protoType == ProtoType.DEFAULT) {
            return;
        }
        Class<?> javaType = argument.getType();
        boolean valid = isInt(javaType) ? is32Bit(protoType) : isLong(javaType) && is64Bit(protoType);
        if (!valid) {
            throw new SerdeException("Property [" + propertyName + "] of [" + messageType.getName()
                + "] declares protobuf type [" + protoType + "], which is incompatible with Java type ["
                + javaType.getName() + "].");
        }
    }

    private static boolean isIntegral(Class<?> type) {
        return isInt(type) || isLong(type) || type == boolean.class || type == Boolean.class;
    }

    private static boolean isInt(Class<?> type) {
        return type == byte.class || type == Byte.class
            || type == short.class || type == Short.class
            || type == char.class || type == Character.class
            || type == int.class || type == Integer.class;
    }

    private static boolean isLong(Class<?> type) {
        return type == long.class || type == Long.class;
    }

    private static boolean is32Bit(ProtoType type) {
        return switch (type) {
            case INT32, SINT32, UINT32, FIXED32, SFIXED32 -> true;
            default -> false;
        };
    }

    private static boolean is64Bit(ProtoType type) {
        return switch (type) {
            case INT64, SINT64, UINT64, FIXED64, SFIXED64 -> true;
            default -> false;
        };
    }

    private static Map<String, Argument<?>> typeBindings(Argument<?> messageArgument) {
        Map<String, Argument<?>> bindings = new HashMap<>(messageArgument.getTypeVariables());
        TypeVariable<?>[] variables = messageArgument.getType().getTypeParameters();
        Argument<?>[] arguments = messageArgument.getTypeParameters();
        for (int i = 0; i < Math.min(variables.length, arguments.length); i++) {
            bindings.put(variables[i].getName(), arguments[i]);
        }
        return bindings;
    }

    @SuppressWarnings("unchecked")
    private static <T> Argument<T> resolveArgument(Argument<T> argument, Map<String, Argument<?>> bounds) {
        Argument<?>[] declaredParameters = argument.getTypeParameters();
        if (argument instanceof GenericPlaceholder<T> placeholder) {
            Argument<?> resolved = bounds.get(placeholder.getVariableName());
            if (resolved != null) {
                return (Argument<T>) resolved.withAnnotationMetadata(argument.getAnnotationMetadata());
            }
        }
        if (declaredParameters.length == 0) {
            return argument;
        }
        Argument<?>[] resolvedParameters = new Argument<?>[declaredParameters.length];
        boolean changed = false;
        for (int i = 0; i < declaredParameters.length; i++) {
            Argument<?> resolved = resolveArgument(declaredParameters[i], bounds);
            resolvedParameters[i] = resolved;
            changed |= resolved != declaredParameters[i];
        }
        return changed
            ? Argument.of(argument.getType(), argument.getName(), argument.getAnnotationMetadata(), resolvedParameters)
            : argument;
    }

    private record FieldShape(Argument<?> valueType,
                              boolean repeated,
                              boolean bytes,
                              boolean repeatedBytes,
                              boolean explicitPresence) {
    }
}
