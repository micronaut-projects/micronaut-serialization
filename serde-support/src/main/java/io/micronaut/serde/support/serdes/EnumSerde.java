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
package io.micronaut.serde.support.serdes;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanMethod;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.EnumBeanIntrospection;
import io.micronaut.core.beans.EnumBeanIntrospection.EnumConstant;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.serde.Decoder;
import org.jspecify.annotations.Nullable;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.FormattedDeserializer;
import io.micronaut.serde.FormatConfiguration;
import io.micronaut.serde.FormattedSerde;
import io.micronaut.serde.FormattedSerializer;
import io.micronaut.serde.SerdeIntrospections;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.SerdeRegistrar;
import io.micronaut.serde.util.CustomizableDeserializer;

import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Serde for handling enums.
 *
 * @param <E> The enum type.
 * @since 1.0.0
 */
final class EnumSerde<E extends Enum<E>> implements CustomizableDeserializer<E>, FormattedSerde<E>, SerdeRegistrar<E> {

    private final SerdeIntrospections introspections;

    EnumSerde(SerdeIntrospections introspections) {
        this.introspections = introspections;
    }

    @Override
    public Deserializer<E> createSpecific(DecoderContext context,
                                          Argument<? super E> type) throws SerdeException {
        Set<DeserializationConfiguration.Feature> features = context.getFeatures();
        boolean acceptCaseInsensitive = features.contains(DeserializationConfiguration.Feature.ACCEPT_CASE_INSENSITIVE_VALUES);
        boolean unknownAsNull = features.contains(DeserializationConfiguration.Feature.READ_UNKNOWN_ENUM_VALUES_AS_NULL);
        boolean unknownUsingDefaultValue = features.contains(DeserializationConfiguration.Feature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE);
        try {
            BeanIntrospection<E> deserializableIntrospection = introspections.getDeserializableIntrospection((Argument<E>) type);
            if (deserializableIntrospection.getConstructor().isAnnotationPresent(Creator.class)) {
                return createEnumCreatorDeserializer(context, deserializableIntrospection);
            } else if (deserializableIntrospection instanceof EnumBeanIntrospection<E> enumBeanIntrospection) {
                List<EnumConstant<E>> constants = enumBeanIntrospection.getConstants();
                for (BeanMethod<? super E, Object> beanMethod : deserializableIntrospection.getBeanMethods()) {
                    if (beanMethod.getAnnotationMetadata().hasDeclaredAnnotation(SerdeConfig.SerValue.class)) {
                        Argument<Object> valueType = beanMethod.getReturnType().asArgument();
                        Deserializer<?> valueDeserializer = context.findDeserializer(valueType);
                        Map<Object, E> cache = CollectionUtils.newHashMap(constants.size());
                        for (EnumConstant<E> enumConstant : constants) {
                            E enumValue = enumConstant.getValue();
                            Object deserializedValue = beanMethod.invoke(enumValue);
                            cache.put(deserializedValue, enumValue);
                        }
                        return new EnumValueDeserializer<>(
                            valueType,
                            valueDeserializer,
                            valueType.isNullable(),
                            cache,
                            acceptCaseInsensitive,
                            findDefaultValue(constants),
                            unknownAsNull,
                            unknownUsingDefaultValue
                        );
                    }
                }
                for (BeanProperty<? super E, Object> beanProperty : deserializableIntrospection.getBeanProperties()) {
                    if (beanProperty.getAnnotationMetadata().hasDeclaredAnnotation(SerdeConfig.SerValue.class)) {
                        Argument<Object> valueType = beanProperty.asArgument();
                        Deserializer<?> valueDeserializer = context.findDeserializer(valueType);
                        Map<Object, E> cache = CollectionUtils.newHashMap(constants.size());
                        for (EnumConstant<E> enumConstant : constants) {
                            E enumValue = enumConstant.getValue();
                            Object deserializedValue = beanProperty.get(enumValue);
                            cache.put(deserializedValue, enumValue);
                        }
                        return new EnumValueDeserializer<>(
                            valueType,
                            valueDeserializer,
                            valueType.isNullable(),
                            cache,
                            acceptCaseInsensitive,
                            findDefaultValue(constants),
                            unknownAsNull,
                            unknownUsingDefaultValue
                        );
                    }
                }
                Map<String, E> cache = CollectionUtils.newHashMap(constants.size());
                for (EnumConstant<E> enumConstant : constants) {
                    E enumValue = enumConstant.getValue();
                    String enumAsString = enumConstant.stringValue(SerdeConfig.class, SerdeConfig.PROPERTY).orElse(enumValue.name());
                    cache.put(enumAsString, enumValue);
                }
                return new EnumPropertyDeserializer<>(
                    cache,
                    acceptCaseInsensitive,
                    false,
                    findDefaultValue(constants),
                    unknownAsNull,
                    unknownUsingDefaultValue
                );
            } else {
                return createEnumCreatorDeserializer(context, deserializableIntrospection);
            }
        } catch (IntrospectionException | SerdeException ignore) {
            // Ignore
        }
        Class<? super E> theType = type.getType();
        if (!theType.isEnum()) {
            throw new SerdeException("Type is not an enum: " + theType);
        }
        Object[] enumConstants = theType.getEnumConstants();
        Map<String, E> cache = CollectionUtils.newHashMap(enumConstants.length);
        for (Object enumConstant : enumConstants) {
            cache.put(((E) enumConstant).name(), (E) enumConstant);
        }
        return new EnumPropertyDeserializer<>(
            cache,
            acceptCaseInsensitive,
            false,
            null,
            unknownAsNull,
            unknownUsingDefaultValue
        );
    }

    @Override
    public Deserializer<E> createSpecific(DecoderContext context,
                                          Argument<? super E> type,
                                          FormatConfiguration format) throws SerdeException {
        Deserializer<E> specific = createSpecific(context, type);
        if (specific instanceof FormattedDeserializer<E> formattedDeserializer) {
            return formattedDeserializer.createSpecific(context, type, format);
        }
        return specific;
    }

    @SuppressWarnings("unchecked")
    private EnumCreatorDeserializer<E> createEnumCreatorDeserializer(DecoderContext context, BeanIntrospection<? super E> deserializableIntrospection) throws SerdeException {
        Argument<?>[] constructorArguments = deserializableIntrospection.getConstructorArguments();
        if (constructorArguments.length != 1) {
            throw new SerdeException("Creator method for Enums must accept exactly 1 argument");
        }
        Argument<Object> argumentType = (Argument<Object>) constructorArguments[0];
        Deserializer<Object> argumentDeserializer = (Deserializer<Object>) context.findDeserializer(argumentType);
        argumentDeserializer = argumentDeserializer.createSpecific(context, argumentType);

        return new EnumCreatorDeserializer<E>(argumentType, argumentDeserializer, deserializableIntrospection, argumentType.isNullable());
    }

    @Override
    public Serializer<E> createSpecific(EncoderContext context,
                                        Argument<? extends E> type) throws SerdeException {
        try {
            BeanIntrospection<E> si = introspections.getSerializableIntrospection((Argument<E>) type);
            for (BeanMethod<? extends E, Object> beanMethod : si.getBeanMethods()) {
                if (beanMethod.getAnnotationMetadata().hasDeclaredAnnotation(SerdeConfig.SerValue.class)) {
                    Argument<Object> valueType = beanMethod.getReturnType().asArgument();
                    Serializer<? super Object> valueSerializer = context.findSerializer(valueType);
                    return new EnumJsonValueSerializer<>(valueType, valueSerializer, beanMethod, null);
                }
            }
            for (BeanProperty<? extends E, Object> beanProperty : si.getBeanProperties()) {
                if (beanProperty.getAnnotationMetadata().hasDeclaredAnnotation(SerdeConfig.SerValue.class)) {
                    Argument<Object> valueType = beanProperty.asArgument();
                    Serializer<? super Object> valueSerializer = context.findSerializer(valueType);
                    return new EnumJsonValueSerializer<>(valueType, valueSerializer, null, beanProperty);
                }
            }
            if (si instanceof EnumBeanIntrospection<E> enumBeanIntrospection) {
                if (enumBeanIntrospection.getConstants().stream()
                    .anyMatch(enumConstant -> enumConstant.stringValue(SerdeConfig.class, SerdeConfig.PROPERTY).isPresent())) {
                    EnumMap<E, String> cache = new EnumMap<>(enumBeanIntrospection.getBeanType());
                    for (EnumConstant<E> enumConstant : enumBeanIntrospection.getConstants()) {
                        E enumValue = enumConstant.getValue();
                        String enumAsString = enumConstant.stringValue(SerdeConfig.class, SerdeConfig.PROPERTY).orElse(enumValue.name());
                        cache.put(enumValue, enumAsString);
                    }
                    return new EnumPropertySerializer<>(cache);
                }
            }
        } catch (IntrospectionException ignore) {
            // ignore
        }
        return new EnumNameSerializer<>();
    }

    @Override
    public Serializer<E> createSpecific(EncoderContext context,
                                        Argument<? extends E> type,
                                        FormatConfiguration format) throws SerdeException {
        Serializer<E> specific = createSpecific(context, type);
        if (specific instanceof FormattedSerializer<E> formattedSerializer) {
            return formattedSerializer.createSpecific(context, type, format);
        }
        return specific;
    }

    static <E extends Enum<E>> Serializer<E> createSerializerForShape(EncoderContext context,
                                                                      Argument<? extends E> type,
                                                                      Serializer<E> specific,
                                                                      FormatConfiguration format) throws SerdeException {
        FormatConfiguration.Shape shape = format.shape();
        if (shape.isNumeric() || shape == FormatConfiguration.Shape.ARRAY) {
            return new EnumOrdinalSerializer<>(specific);
        }
        if (shape.isPojoShape()) {
            return objectSerializer(context, type);
        }
        if (shape == FormatConfiguration.Shape.BOOLEAN || shape == FormatConfiguration.Shape.BINARY) {
            throw new SerdeException("Unsupported serialization shape (%s) for Enum %s, not supported as property annotation".formatted(shape, type.getType().getName()));
        }
        return specific;
    }

    @Nullable
    private static <E extends Enum<E>> E findDefaultValue(List<EnumConstant<E>> constants) {
        for (EnumConstant<E> constant : constants) {
            if (constant.getAnnotationMetadata().hasAnnotation(SerdeConfig.SerEnumDefaultValue.class)) {
                return constant.getValue();
            }
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static <E extends Enum<E>> Serializer<E> objectSerializer(EncoderContext context,
                                                              Argument<? extends E> type) throws SerdeException {
        Serializer<E> objectSerializer = (Serializer<E>) context.findSerializer((Argument) Argument.OBJECT_ARGUMENT);
        return objectSerializer.createSpecific(context, type);
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends E> type, E value) throws IOException {
        encoder.encodeString(value.name());
    }

    @Override
    public Argument<E> getType() {
        return (Argument) Argument.ofTypeVariable(Enum.class, "E");
    }

    static <E extends Enum<E>> IOException failedToDeserialize(Decoder decoder, Map<?, E> values, @Nullable Object value) {
        String allowedValues = values.keySet().stream()
            .map(Object::toString)
            .collect(Collectors.joining(", "));
        String enumType = values.values().stream()
            .findFirst()
            .map(v -> v.getDeclaringClass().getName())
            .orElse("java.lang.Enum");
        String detail = "Expected one of [%s] but was '%s'".formatted(allowedValues, value);
        return decoder.createDeserializationException("Cannot deserialize value of type `%s` due to: %s".formatted(enumType, detail), value);
    }

}

final class EnumOrdinalSerializer<E extends Enum<E>> implements Serializer<E> {
    private final Serializer<E> delegate;

    EnumOrdinalSerializer(Serializer<E> delegate) {
        this.delegate = delegate;
    }

    @Override
    public void serialize(Encoder encoder,
                          EncoderContext context,
                          Argument<? extends E> type,
                          E value) throws IOException {
        encoder.encodeInt(value.ordinal());
    }

    @Override
    public boolean isEmpty(EncoderContext context, @Nullable E value) {
        return delegate.isEmpty(context, value);
    }

    @Override
    public boolean isAbsent(EncoderContext context, @Nullable E value) {
        return delegate.isAbsent(context, value);
    }

    @Override
    public boolean isDefault(EncoderContext context, E value) {
        return delegate.isDefault(context, value);
    }
}

/**
 * Deserializer for enums with json creator.
 *
 * @param <E> The enum type
 */
final class EnumCreatorDeserializer<E extends Enum<E>> implements Deserializer<E> {

    private final Argument<Object> argumentType;
    private final Deserializer<Object> argumentDeserializer;
    private final BeanIntrospection<? super E> deserializableIntrospection;
    private final boolean allowNull;

    public EnumCreatorDeserializer(
        Argument<Object> argumentType,
        Deserializer<Object> argumentDeserializer,
        BeanIntrospection<? super E> deserializableIntrospection,
        boolean allowNull) {
        this.argumentType = argumentType;
        this.argumentDeserializer = argumentDeserializer;
        this.deserializableIntrospection = deserializableIntrospection;
        this.allowNull = allowNull;
    }

    private E transform(@Nullable Object v) {
        try {
            return (E) deserializableIntrospection.instantiate(!allowNull, new Object[]{v});
        } catch (IllegalArgumentException e) {
            if (v instanceof String string) {
                try {
                    return (E) deserializableIntrospection.instantiate(!allowNull, new Object[]{string.toUpperCase(Locale.ENGLISH)});
                } catch (IllegalArgumentException ex) {
                    // throw original
                    throw e;
                }
            } else {
                // throw original
                throw e;
            }
        }
    }

    @Override
    public E deserialize(Decoder decoder, DecoderContext context, Argument<? super E> type) throws IOException {
        return transform(argumentDeserializer.deserialize(decoder, context, argumentType));
    }

    @Override
    public @Nullable E deserializeNullable(Decoder decoder, DecoderContext context, Argument<? super E> type) throws IOException {
        Object v = argumentDeserializer.deserializeNullable(decoder, context, argumentType);
        if (!allowNull && v == null) {
            return null;
        }
        return transform(v);
    }
}

final class EnumValueDeserializer<E extends Enum<E>> implements FormattedDeserializer<E> {

    private final Argument<Object> valueType;
    private final Deserializer<?> valueDeserializer;
    private final boolean allowNull;
    private final Map<Object, E> cache;
    private final boolean acceptCaseInsensitive;
    @Nullable
    private final E defaultValue;
    private final boolean unknownAsNull;
    private final boolean unknownUsingDefaultValue;

    EnumValueDeserializer(Argument<Object> valueType,
                          Deserializer<?> valueDeserializer,
                          boolean allowNull,
                          Map<Object, E> cache,
                          boolean acceptCaseInsensitive,
                          @Nullable E defaultValue) {
        this(valueType, valueDeserializer, allowNull, cache, acceptCaseInsensitive, defaultValue, false, false);
    }

    EnumValueDeserializer(Argument<Object> valueType,
                          Deserializer<?> valueDeserializer,
                          boolean allowNull,
                          Map<Object, E> cache,
                          boolean acceptCaseInsensitive,
                          @Nullable E defaultValue,
                          boolean unknownAsNull,
                          boolean unknownUsingDefaultValue) {
        this.valueType = valueType;
        this.valueDeserializer = valueDeserializer;
        this.allowNull = allowNull;
        this.cache = cache;
        this.acceptCaseInsensitive = acceptCaseInsensitive;
        this.defaultValue = defaultValue;
        this.unknownAsNull = unknownAsNull;
        this.unknownUsingDefaultValue = unknownUsingDefaultValue;
    }

    @Override
    public Deserializer<E> createSpecific(DecoderContext context,
                                          Argument<? super E> type,
                                          FormatConfiguration format) {
        Set<DeserializationConfiguration.Feature> features = context.getFeatures();
        return new EnumValueDeserializer<>(
            valueType,
            valueDeserializer,
            allowNull,
            cache,
            features.contains(DeserializationConfiguration.Feature.ACCEPT_CASE_INSENSITIVE_VALUES),
            defaultValue,
            features.contains(DeserializationConfiguration.Feature.READ_UNKNOWN_ENUM_VALUES_AS_NULL),
            features.contains(DeserializationConfiguration.Feature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
        );
    }

    @Nullable
    private E transform(Decoder decoder, @Nullable Object value) throws IOException {
        E enumValue = cache.get(value);
        if (enumValue != null) {
            return enumValue;
        }
        if (acceptCaseInsensitive) {
            for (Map.Entry<Object, E> e : cache.entrySet()) {
                if (e.getKey() instanceof String key && value instanceof String val) {
                    if (key.equalsIgnoreCase(val)) {
                        return e.getValue();
                    }
                } else {
                    break;
                }
            }
        }
        if (unknownUsingDefaultValue && defaultValue != null) {
            return defaultValue;
        }
        if (unknownAsNull) {
            return null;
        }
        throw EnumSerde.failedToDeserialize(decoder, cache, value);
    }

    @Override
    public E deserialize(Decoder decoder, DecoderContext context, Argument<? super E> type) throws IOException {
        Object v = valueDeserializer.deserialize(decoder, context, valueType);
        E enumValue = transform(decoder, v);
        if (enumValue == null) {
            throw EnumSerde.failedToDeserialize(decoder, cache, v);
        }
        return enumValue;
    }

    @Override
    public @Nullable E deserializeNullable(Decoder decoder, DecoderContext context, Argument<? super E> type) throws IOException {
        Object v = valueDeserializer.deserializeNullable(decoder, context, valueType);
        if (!allowNull && v == null) {
            return null;
        }
        return transform(decoder, v);
    }
}

final class EnumJsonValueSerializer<E extends Enum<E>> implements FormattedSerializer<E> {

    private final Argument<Object> valueType;
    private final Serializer<? super Object> valueSerializer;
    @Nullable
    private final BeanMethod<? extends E, Object> beanMethod;
    @Nullable
    private final BeanProperty<? extends E, Object> beanProperty;

    EnumJsonValueSerializer(Argument<Object> valueType,
                            Serializer<? super Object> valueSerializer,
                            @Nullable BeanMethod<? extends E, Object> beanMethod,
                            @Nullable BeanProperty<? extends E, Object> beanProperty) {
        this.valueType = valueType;
        this.valueSerializer = valueSerializer;
        this.beanMethod = beanMethod;
        this.beanProperty = beanProperty;
    }

    @Override
    public Serializer<E> createSpecific(EncoderContext context,
                                        Argument<? extends E> type,
                                        FormatConfiguration format) throws SerdeException {
        FormatConfiguration.Shape shape = format.shape();
        if (shape.isPojoShape()) {
            return EnumSerde.objectSerializer(context, type);
        }
        if (shape == FormatConfiguration.Shape.BOOLEAN || shape == FormatConfiguration.Shape.BINARY) {
            throw new SerdeException("Unsupported serialization shape (%s) for Enum %s, not supported as property annotation".formatted(shape, type.getType().getName()));
        }
        return this;
    }

    @Override
    public void serialize(Encoder encoder,
                          EncoderContext context,
                          Argument<? extends E> type,
                          E value) throws IOException {
        Object result;
        if (beanMethod != null) {
            result = ((BeanMethod) beanMethod).invoke(value);
        } else {
            result = ((BeanProperty) Objects.requireNonNull(beanProperty)).get(value);
        }
        if (result == null) {
            encoder.encodeNull();
        } else {
            valueSerializer.serialize(encoder, context, valueType, result);
        }
    }
}

final class EnumNameSerializer<E extends Enum<E>> implements FormattedSerializer<E> {

    @Override
    public Serializer<E> createSpecific(EncoderContext context,
                                        Argument<? extends E> type,
                                        FormatConfiguration format) throws SerdeException {
        return EnumSerde.createSerializerForShape(context, type, this, format);
    }

    @Override
    public void serialize(Encoder encoder,
                          EncoderContext context,
                          Argument<? extends E> type,
                          E value) throws IOException {
        encoder.encodeString(value.name());
    }
}

final class EnumPropertySerializer<E extends Enum<E>> implements FormattedSerializer<E> {

    private final EnumMap<E, String> cache;

    EnumPropertySerializer(EnumMap<E, String> cache) {
        this.cache = cache;
    }

    @Override
    public Serializer<E> createSpecific(EncoderContext context,
                                        Argument<? extends E> type,
                                        FormatConfiguration format) throws SerdeException {
        return EnumSerde.createSerializerForShape(context, type, this, format);
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends E> type, E value) throws IOException {
        encoder.encodeString(Objects.requireNonNull(cache.get(value)));
    }
}

final class EnumPropertyDeserializer<E extends Enum<E>> implements FormattedDeserializer<E> {

    private final Map<String, E> cache;
    private final boolean acceptCaseInsensitive;
    private final boolean ordinal;
    @Nullable
    private final E defaultValue;
    private final boolean unknownAsNull;
    private final boolean unknownUsingDefaultValue;
    private final Object[] constants;
    private final String enumType;

    EnumPropertyDeserializer(Map<String, E> cache, boolean acceptCaseInsensitive, @Nullable E defaultValue) {
        this(cache, acceptCaseInsensitive, false, defaultValue, false, false);
    }

    EnumPropertyDeserializer(Map<String, E> cache,
                             boolean acceptCaseInsensitive,
                             boolean ordinal,
                             @Nullable E defaultValue,
                             boolean unknownAsNull,
                             boolean unknownUsingDefaultValue) {
        this.cache = cache;
        this.acceptCaseInsensitive = acceptCaseInsensitive;
        this.ordinal = ordinal;
        this.defaultValue = defaultValue;
        this.unknownAsNull = unknownAsNull;
        this.unknownUsingDefaultValue = unknownUsingDefaultValue;
        E firstValue = cache.values().stream().findFirst().orElse(null);
        this.constants = firstValue == null ? new Object[0] : firstValue.getDeclaringClass().getEnumConstants();
        this.enumType = firstValue == null ? Enum.class.getName() : firstValue.getDeclaringClass().getName();
    }

    @Override
    public Deserializer<E> createSpecific(DecoderContext context,
                                          Argument<? super E> type,
                                          FormatConfiguration format) {
        FormatConfiguration.Shape shape = format.shape();
        boolean ordinal = this.ordinal || shape.isNumeric() || shape == FormatConfiguration.Shape.ARRAY;
        Set<DeserializationConfiguration.Feature> features = context.getFeatures();
        return new EnumPropertyDeserializer<>(
            cache,
            features.contains(DeserializationConfiguration.Feature.ACCEPT_CASE_INSENSITIVE_VALUES),
            ordinal,
            defaultValue,
            features.contains(DeserializationConfiguration.Feature.READ_UNKNOWN_ENUM_VALUES_AS_NULL),
            features.contains(DeserializationConfiguration.Feature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
        );
    }

    @Override
    public E deserialize(Decoder decoder, DecoderContext context, Argument<? super E> type) throws IOException {
        if (ordinal) {
            return deserializeOrdinal(decoder);
        }
        String value = decoder.decodeString();
        return deserializeString(decoder, value);
    }

    @Override
    public @Nullable E deserializeNullable(Decoder decoder, DecoderContext context, Argument<? super E> type) throws IOException {
        if (decoder.decodeNull()) {
            return null;
        }
        if (ordinal) {
            return deserializeOrdinalNullable(decoder);
        }
        String value = decoder.decodeString();
        return deserializeStringNullable(decoder, value);
    }

    private E deserializeString(Decoder decoder, String value) throws IOException {
        E result = cache.get(value);
        if (result != null) {
            return result;
        }
        if (acceptCaseInsensitive) {
            for (Map.Entry<String, E> e : cache.entrySet()) {
                if (e.getKey().equalsIgnoreCase(value)) {
                    return e.getValue();
                }
            }
        }
        E unknown = handleUnknown();
        if (unknown != null) {
            return unknown;
        }
        throw EnumSerde.failedToDeserialize(decoder, cache, value);
    }

    private @Nullable E deserializeStringNullable(Decoder decoder, String value) throws IOException {
        E result = cache.get(value);
        if (result != null) {
            return result;
        }
        if (acceptCaseInsensitive) {
            for (Map.Entry<String, E> e : cache.entrySet()) {
                if (e.getKey().equalsIgnoreCase(value)) {
                    return e.getValue();
                }
            }
        }
        E unknown = handleUnknown();
        if (unknown != null || unknownAsNull) {
            return unknown;
        }
        throw EnumSerde.failedToDeserialize(decoder, cache, value);
    }

    private E deserializeOrdinal(Decoder decoder) throws IOException {
        int ordinal = decoder.decodeInt();
        if (ordinal >= 0 && ordinal < constants.length) {
            return (E) constants[ordinal];
        }
        E unknown = handleUnknown();
        if (unknown != null || unknownAsNull) {
            if (unknown != null) {
                return unknown;
            }
            throw EnumSerde.failedToDeserialize(decoder, cache, null);
        }
        throw decoder.createDeserializationException(
            "Cannot deserialize value of type `%s` due to: ordinal index outside legal index range [0..%s]".formatted(
                enumType,
                constants.length - 1
            ),
            ordinal
        );
    }

    private @Nullable E deserializeOrdinalNullable(Decoder decoder) throws IOException {
        int ordinal = decoder.decodeInt();
        if (ordinal >= 0 && ordinal < constants.length) {
            return (E) constants[ordinal];
        }
        E unknown = handleUnknown();
        if (unknown != null || unknownAsNull) {
            return unknown;
        }
        throw decoder.createDeserializationException(
            "Cannot deserialize value of type `%s` due to: ordinal index outside legal index range [0..%s]".formatted(
                enumType,
                constants.length - 1
            ),
            ordinal
        );
    }

    @Nullable
    private E handleUnknown() {
        if (unknownUsingDefaultValue && defaultValue != null) {
            return defaultValue;
        }
        return null;
    }
}
