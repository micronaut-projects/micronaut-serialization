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
import io.micronaut.core.beans.EnumBeanIntrospection;
import io.micronaut.core.beans.EnumBeanIntrospection.EnumConstant;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.SerdeIntrospections;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.SerdeRegistrar;

import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Serde for handling enums.
 *
 * @param <E> The enum type.
 * @since 1.0.0
 */
final class EnumSerde<E extends Enum<E>> implements SerdeRegistrar<E> {
    private final SerdeIntrospections introspections;
    private final EnumCache<E> enumCache = new EnumCache<>();

    EnumSerde(SerdeIntrospections introspections) {
        this.introspections = introspections;
    }

    @Override
    @NonNull
    public E deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super E> type) throws IOException {
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
            BeanIntrospection<? super E> deserializableIntrospection = introspections.getDeserializableIntrospection(type);
            if (deserializableIntrospection.getConstructor().isAnnotationPresent(Creator.class)) {
                return createEnumCreatorDeserializer(context, deserializableIntrospection);
            }
            EnumValueCache<E> valueCache = enumCache.forDeserialization(deserializableIntrospection);
            for (BeanMethod<? super E, Object> beanMethod : deserializableIntrospection.getBeanMethods()) {
                if (beanMethod.getAnnotationMetadata().hasDeclaredAnnotation(SerdeConfig.SerValue.class)) {
                    Argument<Object> valueType = beanMethod.getReturnType().asArgument();
                    Deserializer<?> valueDeserializer = context.findDeserializer(valueType);
                    return new EnumValueDeserializer<E>(valueType, valueDeserializer, beanMethod::invoke, valueType.isNullable(), valueCache);
                }
            }
            for (BeanProperty<? super E, Object> beanProperty : deserializableIntrospection.getBeanProperties()) {
                if (beanProperty.getAnnotationMetadata().hasDeclaredAnnotation(SerdeConfig.SerValue.class)) {
                    Argument<Object> valueType = beanProperty.asArgument();
                    Deserializer<?> valueDeserializer = context.findDeserializer(valueType);
                    return new EnumValueDeserializer<E>(valueType, valueDeserializer, beanProperty::get, valueType.isNullable(), valueCache);
                }
            }
            if (valueCache.hasPropertyAnnotation()) {
                return new EnumPropertyDeserializer<E>(valueCache);
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
            BeanIntrospection<? extends E> si = introspections.getSerializableIntrospection(type);
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
            EnumValueCache<E> valueCache = enumCache.forSerialization(si);
            if (valueCache.hasPropertyAnnotation()) {
                return new EnumPropertySerializer<>(valueCache);
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
        return transform(v);
    }
}

final class EnumValueDeserializer<E extends Enum<E>> implements Deserializer<E> {

    private final Argument<Object> valueType;
    private final Deserializer<?> valueDeserializer;
    private final Function<EnumConstant<E>, Optional<Object>> valueExtractor;
    private final boolean allowNull;
    private final EnumValueCache<E> valueCache;

    EnumValueDeserializer(Argument<Object> valueType, Deserializer<?> valueDeserializer, Function<E, Object> valueExtractor, boolean allowNull, EnumValueCache<E> valueCache) {
        this.valueType = valueType;
        this.valueDeserializer = valueDeserializer;
        this.valueExtractor = enumConstant -> Optional.ofNullable(valueExtractor.apply(enumConstant.getValue()));
        this.allowNull = allowNull;
        this.valueCache = valueCache;
    }

    @NonNull
    private E transform(@NonNull Decoder decoder, Object value) throws IOException {
        valueCache.calculateValues(valueExtractor);

        return valueCache.getDeserialized(value)
            .orElseThrow(() -> {
                String allowedValues = valueCache.getSerializedValues().stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
                return decoder.createDeserializationException("Expected one of [%s] but was '%s'".formatted(allowedValues, value), value);
            });
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
        return transform(decoder, v);
    }
}

final class EnumPropertySerializer<E extends Enum<E>> implements Serializer<E> {

    private final EnumValueCache<E> valueCache;

    EnumPropertySerializer(EnumValueCache<E> valueCache) {
        this.valueCache = valueCache;
    }

    @Override
    public void serialize(@NonNull Encoder encoder, @NonNull EncoderContext context, @NonNull Argument<? extends E> type, E value) throws IOException {
        valueCache.calculateValues(enumConstant -> enumConstant.stringValue(SerdeConfig.class, SerdeConfig.PROPERTY));

        String result = valueCache.getSerialized(value)
            .map(String.class::cast)
            .orElse(value.name());

        encoder.encodeString(result);
    }
}

final class EnumPropertyDeserializer<E extends Enum<E>> implements Deserializer<E> {
    private final EnumValueCache<E> valueCache;

    EnumPropertyDeserializer(EnumValueCache<E> valueCache) {
        this.valueCache = valueCache;
    }

    @Override
    public E deserialize(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super E> type) throws IOException {
        valueCache.calculateValues(enumConstant -> enumConstant.stringValue(SerdeConfig.class, SerdeConfig.PROPERTY));

        String value = decoder.decodeString();

        Optional<E> result = valueCache.getDeserialized(value);
        if (result.isPresent()) {
            return result.get();
        }

        @SuppressWarnings("rawtypes") final Class t = type.getType();
        try {
            return (E) Enum.valueOf(t, value);
        } catch (IllegalArgumentException e) {
            // try upper case
            try {
                return (E) Enum.valueOf(t, value.toUpperCase(Locale.ENGLISH));
            } catch (Exception ex) {
                // throw original
                throw e;
            }
        }
    }
}

final class EnumCache<E extends Enum<E>> {
    private final Map<Argument<E>, EnumValueCache<E>> cache = new HashMap<>();

    @SuppressWarnings("unchecked")
    @NonNull
    EnumValueCache<E> forSerialization(@NonNull BeanIntrospection<? extends E> introspection) {
        return forEnum((BeanIntrospection<E>) introspection);
    }

    @SuppressWarnings("unchecked")
    @NonNull
    public EnumValueCache<E> forDeserialization(@NonNull BeanIntrospection<? super E> introspection) {
        return forEnum((BeanIntrospection<E>) introspection);
    }

    private EnumValueCache<E> forEnum(BeanIntrospection<E> introspection) {
        return cache.computeIfAbsent(introspection.asArgument(), key -> new EnumValueCache<>(introspection));
    }
}

final class EnumValueCache<E extends Enum<E>> {
    private final EnumBeanIntrospection<E> introspection;
    private final Map<E, Object> deserializedCache;
    private final Map<Object, E> serializedCache;
    private Boolean hasPropertyAnnotation = null;
    private boolean calculated = false;

    public EnumValueCache(BeanIntrospection<E> introspection) {
        this.introspection = (EnumBeanIntrospection<E>) introspection;
        this.deserializedCache = new EnumMap<>(introspection.getBeanType());
        this.serializedCache = new HashMap<>();
    }

    public Optional<E> getDeserialized(Object value) {
        return Optional.ofNullable(serializedCache.get(value));
    }

    public Optional<Object> getSerialized(E value) {
        return Optional.ofNullable(deserializedCache.get(value));
    }

    public Set<Object> getSerializedValues() {
        return serializedCache.keySet();
    }

    public <V> void calculateValues(Function<EnumConstant<E>, Optional<V>> valueExtractor) {
        if (calculated) {
            return;
        }

        for (EnumConstant<E> enumConstant : introspection.getConstants()) {
            E deserialized = enumConstant.getValue();
            valueExtractor.apply(enumConstant)
                .ifPresent(serialized -> store(deserialized, serialized));
        }

        calculated = true;
    }

    public boolean hasPropertyAnnotation() {
        if (hasPropertyAnnotation == null) {
            hasPropertyAnnotation = introspection.getConstants().stream()
                .anyMatch(enumConstant -> enumConstant.stringValue(SerdeConfig.class, SerdeConfig.PROPERTY).isPresent());
        }

        return hasPropertyAnnotation;
    }

    private void store(E deserialized, Object serialized) {
        deserializedCache.put(deserialized, serialized);
        serializedCache.put(serialized, deserialized);
    }
}
