/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.serde.support.serializers;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Keys;
import io.micronaut.serde.KeysAwareEncoder;
import io.micronaut.serde.ObjectSerializer;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.exceptions.path.ReferencePath;
import io.micronaut.serde.support.util.DecoderValueKind;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;

/**
 * Runtime object serializer optimized for repeatedly writing the same bean shape.
 * It snapshots the bean properties into arrays so the hot path can index directly
 * into properties, arguments, reference paths, serializers, scalar value kinds,
 * and precomputed {@link Keys}. Property names are written through
 * {@link KeysAwareEncoder} so encoders that support indexed keys can avoid
 * repeated string lookup or allocation.
 * <p>
 * If every property serializer is available when this serializer is constructed,
 * serialization uses a resolved path that skips per-property serializer lookup.
 * Scalar serializers that expose a {@link DecoderValueKind.Provider} are also
 * written directly with the corresponding encoder primitive method when no
 * property-level formatting or feature override is active.
 * <p>
 * The retained unrolling strategy is a smaller variant of the Jackson-style
 * {@code UnrolledBeanSerializer} experiment. Instead of storing a separate
 * field for each property and dispatching small beans through a size-specific
 * fall-through switch, this serializer keeps the shared array-based machinery
 * and emits four consecutive property writes in each counted-loop iteration.
 * A fall-through tail switch then writes the remaining zero to three
 * properties. That keeps the hot path free of iterator allocation and reduces
 * loop branches without duplicating serializer state or error-path handling.
 * <p>
 * The {@code NON_NULL}, {@code NON_ABSENT} and {@code NON_EMPTY} inclusions are
 * checked here as well, as {@code NON_EMPTY} is the default inclusion. A primitive
 * written directly is never skipped: the serializers that write it treat only
 * {@code null} as empty or absent.
 *
 * @param <T> The bean type
 * @author Denis Stepanov
 * @since 1.0
 */
@Internal
final class SimpleObjectSerializer<T> implements ObjectSerializer<T> {

    private static final byte ALWAYS = 0;
    private static final byte NON_NULL = 1;
    private static final byte NON_ABSENT = 2;
    private static final byte NON_EMPTY = 3;

    private final SerBean.SerProperty<T, Object>[] writeProperties;
    private final @Nullable Serializer<Object>[] serializers;
    private final Argument<Object>[] arguments;
    private final ReferencePath[] referencePaths;
    private final byte[] valueKinds;
    private final byte[] primitiveValueKinds;
    private final byte[] inclusions;
    private final Keys keys;
    private final boolean serializersResolved;

    @SuppressWarnings({"unchecked"})
    SimpleObjectSerializer(SerBean<T> serBean) {
        this.writeProperties = serBean.writeProperties.toArray(SerBean.SerProperty[]::new);
        this.serializers = new Serializer[writeProperties.length];
        this.arguments = new Argument[writeProperties.length];
        this.referencePaths = new ReferencePath[writeProperties.length];
        this.valueKinds = new byte[writeProperties.length];
        this.primitiveValueKinds = new byte[writeProperties.length];
        this.inclusions = new byte[writeProperties.length];
        boolean resolved = true;
        for (int i = 0; i < writeProperties.length; i++) {
            SerBean.SerProperty<T, Object> property = writeProperties[i];
            Serializer<Object> serializer = property.serializer;
            serializers[i] = serializer;
            arguments[i] = property.argument;
            referencePaths[i] = property.getReferencePath();
            byte valueKind = serializer == null ? DecoderValueKind.NONE_CODE : directValueKind(property, serializer);
            valueKinds[i] = valueKind;
            primitiveValueKinds[i] = property.primitive ? valueKind : DecoderValueKind.NONE_CODE;
            inclusions[i] = inclusionCode(property.include);
            resolved &= serializer != null;
        }
        this.keys = serBean.propertyKeys;
        this.serializersResolved = resolved;
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException {
        KeysAwareEncoder childEncoder = KeysAwareEncoder.of(encoder.encodeObject(type));
        if (serializersResolved) {
            serializeResolvedProperties(childEncoder, context, value);
            childEncoder.finishStructure();
            return;
        }
        serializeProperties(childEncoder, context, value);
        childEncoder.finishStructure();
    }

    private void serializeProperties(KeysAwareEncoder childEncoder, EncoderContext context, T value) throws IOException {
        int i = 0;
        int left = writeProperties.length;
        if (left > 3) {
            do {
                serializeProperty(childEncoder, context, value, i);
                serializeProperty(childEncoder, context, value, i + 1);
                serializeProperty(childEncoder, context, value, i + 2);
                serializeProperty(childEncoder, context, value, i + 3);
                i += 4;
                left -= 4;
            } while (left > 3);
        }
        switch (left) {
            case 3:
                serializeProperty(childEncoder, context, value, i++);
                // fall through
            case 2:
                serializeProperty(childEncoder, context, value, i++);
                // fall through
            case 1:
                serializeProperty(childEncoder, context, value, i);
                // fall through
            default:
        }
    }

    private void serializeResolvedProperties(KeysAwareEncoder childEncoder, EncoderContext context, T value) throws IOException {
        int i = 0;
        int left = writeProperties.length;
        if (left > 3) {
            do {
                serializeResolvedProperty(childEncoder, context, value, i);
                serializeResolvedProperty(childEncoder, context, value, i + 1);
                serializeResolvedProperty(childEncoder, context, value, i + 2);
                serializeResolvedProperty(childEncoder, context, value, i + 3);
                i += 4;
                left -= 4;
            } while (left > 3);
        }
        switch (left) {
            case 3:
                serializeResolvedProperty(childEncoder, context, value, i++);
                // fall through
            case 2:
                serializeResolvedProperty(childEncoder, context, value, i++);
                // fall through
            case 1:
                serializeResolvedProperty(childEncoder, context, value, i);
                // fall through
            default:
        }
    }

    @Override
    public void serializeInto(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException {
        KeysAwareEncoder keysAwareEncoder = KeysAwareEncoder.of(encoder);
        if (serializersResolved) {
            serializeResolvedPropertiesInto(keysAwareEncoder, context, value);
            return;
        }
        serializePropertiesInto(keysAwareEncoder, context, value);
    }

    private void serializePropertiesInto(KeysAwareEncoder keysAwareEncoder, EncoderContext context, T value) throws IOException {
        int i = 0;
        int left = writeProperties.length;
        if (left > 3) {
            do {
                serializePropertyInto(keysAwareEncoder, context, value, i);
                serializePropertyInto(keysAwareEncoder, context, value, i + 1);
                serializePropertyInto(keysAwareEncoder, context, value, i + 2);
                serializePropertyInto(keysAwareEncoder, context, value, i + 3);
                i += 4;
                left -= 4;
            } while (left > 3);
        }
        switch (left) {
            case 3:
                serializePropertyInto(keysAwareEncoder, context, value, i++);
                // fall through
            case 2:
                serializePropertyInto(keysAwareEncoder, context, value, i++);
                // fall through
            case 1:
                serializePropertyInto(keysAwareEncoder, context, value, i);
                // fall through
            default:
        }
    }

    private void serializeResolvedPropertiesInto(KeysAwareEncoder keysAwareEncoder, EncoderContext context, T value) throws IOException {
        int i = 0;
        int left = writeProperties.length;
        if (left > 3) {
            do {
                serializeResolvedPropertyInto(keysAwareEncoder, context, value, i);
                serializeResolvedPropertyInto(keysAwareEncoder, context, value, i + 1);
                serializeResolvedPropertyInto(keysAwareEncoder, context, value, i + 2);
                serializeResolvedPropertyInto(keysAwareEncoder, context, value, i + 3);
                i += 4;
                left -= 4;
            } while (left > 3);
        }
        switch (left) {
            case 3:
                serializeResolvedPropertyInto(keysAwareEncoder, context, value, i++);
                // fall through
            case 2:
                serializeResolvedPropertyInto(keysAwareEncoder, context, value, i++);
                // fall through
            case 1:
                serializeResolvedPropertyInto(keysAwareEncoder, context, value, i);
                // fall through
            default:
        }
    }

    private void serializeProperty(KeysAwareEncoder encoder, EncoderContext context, T value, int index) throws IOException {
        try {
            serializePropertyInto(encoder, context, value, index);
        } catch (SerdeException e) {
            e.getPath().add(referencePaths[index]);
            throw e;
        } catch (Exception e) {
            throw propertyError(index, e);
        }
    }

    private void serializeResolvedProperty(KeysAwareEncoder encoder, EncoderContext context, T value, int index) throws IOException {
        try {
            serializeResolvedPropertyInto(encoder, context, value, index);
        } catch (SerdeException e) {
            e.getPath().add(referencePaths[index]);
            throw e;
        } catch (Exception e) {
            throw propertyError(index, e);
        }
    }

    private void serializePropertyInto(KeysAwareEncoder encoder, EncoderContext context, T value, int index) throws IOException {
        SerBean.SerProperty<T, Object> property = writeProperties[index];
        byte valueKind = valueKinds[index];
        Serializer<Object> serializer = serializers[index];
        byte primitiveValueKind = primitiveValueKinds[index];
        if (valueKind == DecoderValueKind.NONE_CODE && serializer == null && property.primitive) {
            serializer = Objects.requireNonNull(property.serializer);
            valueKind = directValueKind(property, serializer);
            primitiveValueKind = valueKind;
        }
        if (primitiveValueKind != DecoderValueKind.NONE_CODE
            && property.serializeDirectPrimitive(encoder, keys, index, value, primitiveValueKind)) {
            return;
        }
        Object v = property.get(value);
        byte inclusion = inclusions[index];
        if (inclusion != ALWAYS) {
            if (serializer == null) {
                serializer = Objects.requireNonNull(property.serializer);
                valueKind = directValueKind(property, serializer);
            }
            if (primitiveValueKind == DecoderValueKind.NONE_CODE && isSkipped(inclusion, serializer, context, v)) {
                return;
            }
        }
        encoder.encodeKey(keys, index);
        serializePropertyValue(encoder, context, index, v, serializer, valueKind);
    }

    private void serializePropertyValue(KeysAwareEncoder encoder,
                                        EncoderContext context,
                                        int index,
                                        @Nullable Object v,
                                        @Nullable Serializer<Object> resolvedSerializer,
                                        byte resolvedValueKind) throws IOException {
        if (v == null) {
            encoder.encodeNull();
            return;
        }
        Serializer<Object> serializer = resolvedSerializer;
        byte valueKind = resolvedValueKind;
        if (serializer == null) {
            SerBean.SerProperty<T, Object> property = writeProperties[index];
            serializer = Objects.requireNonNull(property.serializer);
            valueKind = directValueKind(property, serializer);
        }
        if (valueKind != DecoderValueKind.NONE_CODE) {
            serializeDirectValue(encoder, v, valueKind);
            return;
        }
        serializer.serialize(encoder, context, arguments[index], v);
    }

    @SuppressWarnings("NullAway")
    private void serializeResolvedPropertyInto(KeysAwareEncoder encoder, EncoderContext context, T value, int index) throws IOException {
        SerBean.SerProperty<T, Object> property = writeProperties[index];
        byte primitiveValueKind = primitiveValueKinds[index];
        Object v;
        if (primitiveValueKind != DecoderValueKind.NONE_CODE) {
            if (property.serializeDirectPrimitive(encoder, keys, index, value, primitiveValueKind)) {
                return;
            }
            v = property.get(value);
            encoder.encodeKey(keys, index);
        } else {
            v = property.get(value);
            byte inclusion = inclusions[index];
            if (inclusion != ALWAYS && isSkipped(inclusion, serializers[index], context, v)) {
                return;
            }
            encoder.encodeKey(keys, index);
        }
        if (v == null) {
            encoder.encodeNull();
            return;
        }
        byte valueKind = valueKinds[index];
        if (valueKind != DecoderValueKind.NONE_CODE) {
            serializeDirectValue(encoder, v, valueKind);
            return;
        }
        serializers[index].serialize(encoder, context, arguments[index], v);
    }

    private SerdeException propertyError(int index, Exception e) {
        SerBean.SerProperty<T, Object> property = writeProperties[index];
        SerdeException serdeException = new SerdeException("Error getting property [" + property.argument + "] of type [" + property.beanType + "]: " + e.getMessage(), e);
        serdeException.getPath().add(referencePaths[index]);
        return serdeException;
    }

    private static boolean isSkipped(byte inclusion, Serializer<Object> serializer, EncoderContext context, @Nullable Object value) {
        return switch (inclusion) {
            case NON_NULL -> value == null;
            case NON_ABSENT -> serializer.isAbsent(context, value);
            case NON_EMPTY -> serializer.isEmpty(context, value);
            default -> false;
        };
    }

    /**
     * Whether this serializer supports the inclusion of a property.
     *
     * @param include The inclusion
     * @return Whether it is supported
     */
    static boolean supportsInclusion(SerdeConfig.SerInclude include) {
        return switch (include) {
            case ALWAYS, NON_NULL, NON_ABSENT, NON_EMPTY -> true;
            default -> false;
        };
    }

    private static byte inclusionCode(SerdeConfig.SerInclude include) {
        return switch (include) {
            case NON_NULL -> NON_NULL;
            case NON_ABSENT -> NON_ABSENT;
            case NON_EMPTY -> NON_EMPTY;
            default -> ALWAYS;
        };
    }

    private static byte directValueKind(SerBean.SerProperty<?, Object> property, Serializer<Object> serializer) {
        if (property.format == null
            && property.featuresWith.isEmpty()
            && property.featuresWithout.isEmpty()
            && serializer instanceof DecoderValueKind.Provider decoderValueKind) {
            return decoderValueKind.decoderValueKind().code();
        }
        return DecoderValueKind.NONE_CODE;
    }

    private static void serializeDirectValue(Encoder encoder, Object value, byte valueKind) throws IOException {
        switch (valueKind) {
            case DecoderValueKind.STRING_CODE -> encoder.encodeString((String) value);
            case DecoderValueKind.BOOLEAN_CODE -> encoder.encodeBoolean((Boolean) value);
            case DecoderValueKind.BYTE_CODE -> encoder.encodeByte((Byte) value);
            case DecoderValueKind.SHORT_CODE -> encoder.encodeShort((Short) value);
            case DecoderValueKind.CHAR_CODE -> encoder.encodeChar((Character) value);
            case DecoderValueKind.INT_CODE -> encoder.encodeInt((Integer) value);
            case DecoderValueKind.LONG_CODE -> encoder.encodeLong((Long) value);
            case DecoderValueKind.FLOAT_CODE -> encoder.encodeFloat((Float) value);
            case DecoderValueKind.DOUBLE_CODE -> encoder.encodeDouble((Double) value);
            default -> throw new IllegalStateException("Unsupported encoder value kind: " + valueKind);
        }
    }

}
