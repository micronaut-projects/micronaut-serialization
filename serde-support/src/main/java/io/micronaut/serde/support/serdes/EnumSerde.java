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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanMethod;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Executable;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.SerdeIntrospections;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.SerdeRegistrar;

import java.io.IOException;
import java.util.Collection;
import java.util.Locale;

/**
 * Serde for handling enums.
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

    @SuppressWarnings("unchecked")
    @Override
    @NonNull
    public Deserializer<E> createSpecific(@NonNull DecoderContext context, @NonNull Argument<? super E> type) {
        try {
            BeanIntrospection<? super E> deserializableIntrospection = introspections.getDeserializableIntrospection(type);
            Argument<?>[] constructorArguments = deserializableIntrospection.getConstructorArguments();
            if (constructorArguments.length != 1) {
                throw new SerdeException("Creator method for Enums must accept exactly 1 argument");
            }
            Argument<Object> argumentType = (Argument<Object>) constructorArguments[0];
            Deserializer<Object> argumentDeserializer = (Deserializer<Object>) context.findDeserializer(argumentType);

            return new EnumCreatorDeserializer<E>(argumentType, argumentDeserializer, deserializableIntrospection, argumentType.isNullable());
        } catch (IntrospectionException | SerdeException e) {
            return this;
        }
    }

    @Override
    @NonNull
    public Serializer<E> createSpecific(@NonNull EncoderContext context, @NonNull Argument<? extends E> type) throws SerdeException {
        try {
            BeanIntrospection<? extends E> si = introspections.getSerializableIntrospection(type);
            Collection<? extends BeanMethod<? extends E, Object>> beanMethods = si.getBeanMethods();
            for (BeanMethod<? extends E, Object> beanMethod : beanMethods) {
                if (beanMethod.getAnnotationMetadata().hasDeclaredAnnotation(SerdeConfig.SerValue.class)) {
                    Argument<Object> valueType = beanMethod.getReturnType().asArgument();
                    Serializer<? super Object> valueSerializer = context.findSerializer(valueType);
                    return (encoder, subContext, subType, value) -> {
                        @SuppressWarnings("unchecked") Object result = ((Executable) beanMethod).invoke(value);
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

    @Override
    public Argument<E> getType() {
        return (Argument) Argument.ofTypeVariable(Enum.class, "E");
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
            return (E) deserializableIntrospection.instantiate(!allowNull, new Object[] { v });
        } catch (IllegalArgumentException e) {
            if (v instanceof String string) {
                try {
                    return (E) deserializableIntrospection.instantiate(!allowNull,  new Object[] { string.toUpperCase(Locale.ENGLISH) });
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
