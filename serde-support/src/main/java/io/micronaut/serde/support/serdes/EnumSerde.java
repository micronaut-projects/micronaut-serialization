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
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
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
            if (deserializableIntrospection instanceof EnumBeanIntrospection<? super E> enumIntrospection) {
                for (BeanMethod<? super E, Object> beanMethod : deserializableIntrospection.getBeanMethods()) {
                    if (beanMethod.getAnnotationMetadata().hasDeclaredAnnotation(SerdeConfig.SerValue.class)) {
                        Argument<Object> valueType = beanMethod.getReturnType().asArgument();
                        Deserializer<?> valueDeserializer = context.findDeserializer(valueType);
                        return new EnumValueDeserializer<E>(valueType, valueDeserializer, enumIntrospection, beanMethod::invoke, valueType.isNullable());
                    }
                }
                for (BeanProperty<? super E, Object> beanProperty : deserializableIntrospection.getBeanProperties()) {
                    if (beanProperty.getAnnotationMetadata().hasDeclaredAnnotation(SerdeConfig.SerValue.class)) {
                        var valueType = beanProperty.asArgument();
                        Deserializer<?> valueDeserializer = context.findDeserializer(valueType);
                        return new EnumValueDeserializer<E>(valueType, valueDeserializer, enumIntrospection, beanProperty::get, valueType.isNullable());
                    }
                }
                boolean hasPropertyAnnotation = enumIntrospection.getConstants().stream()
                    .anyMatch(enumConstant -> enumConstant.stringValue(SerdeConfig.class, SerdeConfig.PROPERTY).isPresent());
                if (hasPropertyAnnotation) {
                    return new EnumPropertyDeserializer<E>(enumIntrospection);
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
            if (si instanceof EnumBeanIntrospection<? extends E> enumIntrospection) {
                boolean hasPropertyAnnotation = enumIntrospection.getConstants().stream()
                    .anyMatch(enumConstant -> enumConstant.stringValue(SerdeConfig.class, SerdeConfig.PROPERTY).isPresent());
                if (hasPropertyAnnotation) {
                    return new EnumPropertySerializer<>(enumIntrospection);
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
    private final EnumBeanIntrospection<? super E> enumIntrospection;
    private final Function<E, Object> valueExtractor;
    private final boolean allowNull;

    public EnumValueDeserializer(Argument<Object> valueType, Deserializer<?> valueDeserializer, EnumBeanIntrospection<? super E> enumIntrospection, Function<E, Object> valueExtractor, boolean allowNull) {
        this.valueType = valueType;
        this.valueDeserializer = valueDeserializer;
        this.enumIntrospection = enumIntrospection;
        this.valueExtractor = valueExtractor;
        this.allowNull = allowNull;
    }

    @NonNull
    private E transform(@NonNull Decoder decoder, Object value) throws IOException {
        for (EnumBeanIntrospection.EnumConstant<? super E> enumConstant : enumIntrospection.getConstants()) {
            E enumValue = (E) enumConstant.getValue();
            Object extractedValue = valueExtractor.apply(enumValue);
            if (Objects.equals(extractedValue, value)) {
                return enumValue;
            }
        }

        var allowedValues = enumIntrospection.getConstants().stream()
            .map(EnumBeanIntrospection.EnumConstant::getValue)
            .map(enumValue -> valueExtractor.apply((E) enumValue))
            .map(Object::toString)
            .collect(Collectors.joining(", "));

        throw decoder.createDeserializationException("Expected one of [%s] but was '%s'".formatted(allowedValues, value), value);
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

    private final EnumBeanIntrospection<? extends E> enumIntrospection;

    EnumPropertySerializer(EnumBeanIntrospection<? extends E> enumIntrospection) {
        this.enumIntrospection = enumIntrospection;
    }

    @Override
    public void serialize(@NonNull Encoder encoder, @NonNull EncoderContext context, @NonNull Argument<? extends E> type, E value) throws IOException {
        for (EnumBeanIntrospection.EnumConstant<? extends E> enumConstant : enumIntrospection.getConstants()) {
            if (enumConstant.getValue() == value) {
                encoder.encodeString(enumConstant.stringValue(SerdeConfig.class, SerdeConfig.PROPERTY).orElse(value.name()));
            }
        }
    }
}

final class EnumPropertyDeserializer<E extends Enum<E>> implements Deserializer<E> {

    private final EnumBeanIntrospection<? super E> enumIntrospection;

    EnumPropertyDeserializer(EnumBeanIntrospection<? super E> enumIntrospection) {
        this.enumIntrospection = enumIntrospection;
    }


    @Override
    public E deserialize(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super E> type) throws IOException {
        var value = decoder.decodeString();

        for (EnumBeanIntrospection.EnumConstant<? super E> enumConstant : enumIntrospection.getConstants()) {
            Optional<String> matchingProperty = enumConstant.stringValue(SerdeConfig.class, SerdeConfig.PROPERTY).filter(propertyValue -> propertyValue.equals(value));
            if (matchingProperty.isPresent()) {
                return (E) enumConstant.getValue();
            }
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
