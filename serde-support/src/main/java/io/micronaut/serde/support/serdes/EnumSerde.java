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
import org.jspecify.annotations.NonNull;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanMethod;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.EnumBeanIntrospection;
import io.micronaut.core.beans.EnumBeanIntrospection.EnumConstant;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
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
import java.util.stream.Collectors;

/**
 * Serde for handling enums.
 *
 * @param <E> The enum type.
 * @since 1.0.0
 */
final class EnumSerde<E extends Enum<E>> implements CustomizableDeserializer<E>, SerdeRegistrar<E> {
    private final SerdeIntrospections introspections;

    EnumSerde(SerdeIntrospections introspections) {
        this.introspections = introspections;
    }

    @Override
    @NonNull
    public Deserializer<E> createSpecific(@NonNull DecoderContext context, @NonNull Argument<? super E> type) throws SerdeException {
        boolean acceptCaseInsensitive = context.getDeserializationConfiguration().map(DeserializationConfiguration::acceptCaseInsensitiveEnums).orElse(false);
        try {
            BeanIntrospection<E> deserializableIntrospection = introspections.getDeserializableIntrospection((Argument<E>) type);
            if (deserializableIntrospection.getConstructor().isAnnotationPresent(Creator.class)) {
                return createEnumCreatorDeserializer(context, deserializableIntrospection);
            }
            if (deserializableIntrospection instanceof EnumBeanIntrospection<E> enumBeanIntrospection) {
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
                        return new EnumValueDeserializer<>(valueType, valueDeserializer, valueType.isNullable(), cache, acceptCaseInsensitive);
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
                        return new EnumValueDeserializer<>(valueType, valueDeserializer, valueType.isNullable(), cache, acceptCaseInsensitive);
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
                    type.isNullable()
                );
            }
            return createEnumCreatorDeserializer(context, deserializableIntrospection);
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
            type.isNullable()
        );
    }

    @SuppressWarnings("unchecked")
    private EnumCreatorDeserializer<E> createEnumCreatorDeserializer(DecoderContext context, BeanIntrospection<? super E> deserializableIntrospection) throws SerdeException {
        Argument<?>[] constructorArguments = deserializableIntrospection.getConstructorArguments();
        if (constructorArguments.length != 1) {
            throw new SerdeException("Creator method for Enums must accept exactly 1 argument");
        }
        Argument<Object> argumentType = (Argument<Object>) constructorArguments[0];
        Deserializer<Object> argumentDeserializer = (Deserializer<Object>) context.findDeserializer(argumentType);

        return new EnumCreatorDeserializer<E>(argumentType, argumentDeserializer, deserializableIntrospection, argumentType.isNullable());
    }

    @Override
    @NonNull
    public Serializer<E> createSpecific(@NonNull EncoderContext context, @NonNull Argument<? extends E> type) throws SerdeException {
        try {
            BeanIntrospection<E> si = introspections.getSerializableIntrospection((Argument<E>) type);
            for (BeanMethod<? extends E, Object> beanMethod : si.getBeanMethods()) {
                if (beanMethod.getAnnotationMetadata().hasDeclaredAnnotation(SerdeConfig.SerValue.class)) {
                    Serializer<? super Object> valueSerializer = context.findSerializer(beanMethod.getReturnType().asArgument());
                    return (encoder, subContext, subType, value) -> {
                        Object result = ((BeanMethod) beanMethod).invoke(value);
                        if (result == null) {
                            encoder.encodeNull();
                        } else {
                            valueSerializer.serialize(encoder, subContext, subType, result);
                        }
                    };
                }
            }
            for (BeanProperty<? extends E, Object> beanProperty : si.getBeanProperties()) {
                if (beanProperty.getAnnotationMetadata().hasDeclaredAnnotation(SerdeConfig.SerValue.class)) {
                    Serializer<? super Object> valueSerializer = context.findSerializer(beanProperty.asArgument());
                    return (encoder, subContext, subType, value) -> {
                        Object result = ((BeanProperty) beanProperty).get(value);
                        if (result == null) {
                            encoder.encodeNull();
                        } else {
                            valueSerializer.serialize(encoder, subContext, subType, result);
                        }
                    };
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
            return this;
        } catch (IntrospectionException e) {
            return this;
        }
    }

    @Override
    public void serialize(Encoder encoder, @NonNull EncoderContext context, @NonNull Argument<? extends E> type, E value) throws IOException {
        encoder.encodeString(value.name());
    }

    @Override
    public Argument<E> getType() {
        return (Argument) Argument.ofTypeVariable(Enum.class, "E");
    }

    @NonNull
    static <E extends Enum<E>> IOException failedToDeserialize(@NonNull Decoder decoder, @NonNull Map<?, E> values, @NonNull Object value) {
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

    @NonNull
    private E transform(Object v) {
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
    public E deserialize(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super E> type) throws IOException {
        return transform(argumentDeserializer.deserialize(decoder, context, argumentType));
    }

    @Override
    public E deserializeNullable(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super E> type) throws IOException {
        Object v = argumentDeserializer.deserializeNullable(decoder, context, argumentType);
        if (!allowNull && v == null) {
            return null;
        }
        if (v instanceof String s && s.isEmpty() && allowNull) {
            return null;
        }
        return transform(v);
    }
}

final class EnumValueDeserializer<E extends Enum<E>> implements Deserializer<E> {

    private final Argument<Object> valueType;
    private final Deserializer<?> valueDeserializer;
    private final boolean allowNull;
    private final Map<Object, E> cache;
    private final boolean acceptCaseInsensitive;

    EnumValueDeserializer(Argument<Object> valueType,
                          Deserializer<?> valueDeserializer,
                          boolean allowNull,
                          Map<Object, E> cache, boolean acceptCaseInsensitive) {
        this.valueType = valueType;
        this.valueDeserializer = valueDeserializer;
        this.allowNull = allowNull;
        this.cache = cache;
        this.acceptCaseInsensitive = acceptCaseInsensitive;
    }

    @NonNull
    private E transform(@NonNull Decoder decoder, Object value) throws IOException {
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
        throw EnumSerde.failedToDeserialize(decoder, cache, value);
    }

    @Override
    public E deserialize(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super E> type) throws IOException {
        return transform(decoder, valueDeserializer.deserialize(decoder, context, valueType));
    }

    @Override
    public E deserializeNullable(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super E> type) throws IOException {
        Object v = valueDeserializer.deserializeNullable(decoder, context, valueType);
        if (!allowNull && v == null) {
            return null;
        }
        if (v instanceof String s && s.isEmpty() && type.isNullable() && !cache.containsKey("")) {
            return null;
        }
        return transform(decoder, v);
    }
}

final class EnumPropertySerializer<E extends Enum<E>> implements Serializer<E> {

    private final EnumMap<E, String> cache;

    EnumPropertySerializer(EnumMap<E, String> cache) {
        this.cache = cache;
    }

    @Override
    public void serialize(@NonNull Encoder encoder, @NonNull EncoderContext context, @NonNull Argument<? extends E> type, E value) throws IOException {
        encoder.encodeString(cache.get(value));
    }
}

final class EnumPropertyDeserializer<E extends Enum<E>> implements Deserializer<E> {

    private final Map<String, E> cache;
    private final boolean acceptCaseInsensitive;
    private final boolean nullable;

    EnumPropertyDeserializer(Map<String, E> cache, boolean acceptCaseInsensitive, boolean nullable) {
        this.cache = cache;
        this.acceptCaseInsensitive = acceptCaseInsensitive;
        this.nullable = nullable;
    }

    @Override
    public E deserialize(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super E> type) throws IOException {
        String value = decoder.decodeString();
        E result = cache.get(value);
        if (result != null) {
            return result;
        }
        if (value.isEmpty() && nullable) {
            return null;
        }
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
        throw EnumSerde.failedToDeserialize(decoder, cache, value);
    }
}
