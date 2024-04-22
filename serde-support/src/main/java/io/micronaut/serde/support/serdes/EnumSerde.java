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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanMethod;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.SerdeIntrospections;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.util.NullableSerde;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Serde for handling enums.
 * @param <E> The enum type.
 * @since 1.0.0
 */
@Singleton
final class EnumSerde<E extends Enum<E>> implements NullableSerde<E> {
    private final SerdeIntrospections introspections;

    EnumSerde(SerdeIntrospections introspections) {
        this.introspections = introspections;
    }

    @Override
    @NonNull
    public E deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super E> type) throws IOException {
        @SuppressWarnings("rawtypes") final Class t = type.getType();
        String s = decoder.decodeString();
        try {
            return (E) Enum.valueOf(t, s);
        } catch (IllegalArgumentException e) {
            // try upper case
            try {
                return (E) Enum.valueOf(t, s.toUpperCase(Locale.ENGLISH));
            } catch (Exception ex) {
                // throw original
                throw e;
            }
        }
    }

    @Override
    @NonNull
    public Deserializer<E> createSpecific(@NonNull DecoderContext context, @NonNull Argument<? super E> type) {
        try {
            BeanIntrospection<E> deserializableIntrospection = introspections.getDeserializableIntrospection((Argument<E>) type);
            if (deserializableIntrospection.getConstructor().isAnnotationPresent(Creator.class)) {
                return createEnumCreatorDeserializer(context, deserializableIntrospection);
            }
            for (BeanMethod<? super E, Object> beanMethod : deserializableIntrospection.getBeanMethods()) {
                if (beanMethod.getAnnotationMetadata().hasDeclaredAnnotation(SerdeConfig.SerValue.class)) {
                    Argument<Object> valueType = beanMethod.getReturnType().asArgument();
                    Deserializer<?> valueDeserializer = context.findDeserializer(valueType);
                    Map<Object, E> cache = new HashMap<>();
                    for (E enumValue: EnumSet.allOf((Class<E>) type.getType())) {
                        Object deserializedValue = beanMethod.invoke(enumValue);
                        cache.put(deserializedValue, enumValue);
                    }
                    return new EnumValueDeserializer<>(valueType, valueDeserializer, valueType.isNullable(), cache);
                }
            }
            for (BeanProperty<? super E, Object> beanProperty : deserializableIntrospection.getBeanProperties()) {
                if (beanProperty.getAnnotationMetadata().hasAnnotation(SerdeConfig.SerValue.class)) {
                    Argument<Object> valueType = beanProperty.asArgument();
                    Deserializer<?> valueDeserializer = context.findDeserializer(valueType);
                    Map<Object, E> cache = new HashMap<>();
                    for (E enumValue: EnumSet.allOf((Class<E>) type.getType())) {
                        Object deserializedValue = beanProperty.get(enumValue);
                        cache.put(deserializedValue, enumValue);
                    }
                    return new EnumValueDeserializer<>(valueType, valueDeserializer, valueType.isNullable(), cache);
                }
            }
            return createEnumCreatorDeserializer(context, deserializableIntrospection);
        } catch (IntrospectionException | SerdeException e) {
            return this;
        }
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
                if (beanProperty.getAnnotationMetadata().hasAnnotation(SerdeConfig.SerValue.class)) {
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
            return this;
        } catch (IntrospectionException e) {
            return this;
        }
    }

    @Override
    public void serialize(Encoder encoder, @NonNull EncoderContext context, @NonNull Argument<? extends E> type, E value) throws IOException {
        encoder.encodeString(value.name());
    }
}

/**
 * Deserializer for enums with json creator.
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
            if (v instanceof String) {
                try {
                    return (E) deserializableIntrospection.instantiate(!allowNull, new Object[]{((String) v).toUpperCase(Locale.ENGLISH)});
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
}

final class EnumValueDeserializer<E extends Enum<E>> implements Deserializer<E> {

    private final Argument<Object> valueType;
    private final Deserializer<?> valueDeserializer;
    private final boolean allowNull;
    private final Map<Object, E> serializedCache;

    EnumValueDeserializer(Argument<Object> valueType,
                          Deserializer<?> valueDeserializer,
                          boolean allowNull,
                          Map<Object, E> serializedCache) {
        this.valueType = valueType;
        this.valueDeserializer = valueDeserializer;
        this.allowNull = allowNull;
        this.serializedCache = serializedCache;
    }

    @NonNull
    private E transform(@NonNull Decoder decoder, Object value) throws IOException {
        E enumValue = serializedCache.get(value);
        if (enumValue == null) {
            String allowedValues = serializedCache.keySet().stream()
                .map(Object::toString)
                .collect(Collectors.joining(", "));
            throw decoder.createDeserializationException(String.format("Expected one of [%s] but was '%s'", allowedValues, value), value);
        }
        return enumValue;
    }

    @Override
    public E deserialize(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super E> type) throws IOException {
        return transform(decoder, valueDeserializer.deserialize(decoder, context, valueType));
    }
}

/**
 * Deserializer for enum sets.
 * @param <E> The enum type
 */
@Singleton
final class EnumSetDeserializer<E extends Enum<E>> implements Deserializer<EnumSet<E>> {

    @Override
    public EnumSet<E> deserialize(Decoder decoder, DecoderContext context, Argument<? super EnumSet<E>> type)
        throws IOException {
        final Argument[] generics = type.getTypeParameters();
        if (ArrayUtils.isEmpty(generics)) {
            throw new SerdeException("Cannot deserialize raw list");
        }
        @SuppressWarnings("unchecked") final Argument<E> generic = (Argument<E>) generics[0];
        final Decoder arrayDecoder = decoder.decodeArray();
        HashSet<E> set = new HashSet<>();
        Deserializer<E> deserializer = context.findDeserializer(type.getTypeParameters()[0])
            .createSpecific(context, type.getTypeParameters()[0]);
        while (arrayDecoder.hasNextArrayValue()) {
            set.add(deserializer.deserialize(arrayDecoder, context, generic));
        }
        arrayDecoder.finishStructure();
        return EnumSet.copyOf(set);
    }
}
