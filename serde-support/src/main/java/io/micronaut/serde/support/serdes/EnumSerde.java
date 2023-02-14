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

import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;

import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanMethod;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.reflect.exception.InstantiationException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Executable;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.SerdeIntrospections;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.deserializers.ObjectDeserializer;
import io.micronaut.serde.util.NullableSerde;
import jakarta.inject.Singleton;

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

    @SuppressWarnings("unchecked")
    @Override
    public Deserializer<E> createSpecific(DecoderContext context, Argument<? super E> type) {
        try {
            BeanIntrospection<? super E> deserializableIntrospection = introspections.getDeserializableIntrospection(type);
            Argument<?>[] constructorArguments = deserializableIntrospection.getConstructorArguments();
            if (constructorArguments.length != 1) {
                throw new SerdeException("Creator method for Enums must accept exactly 1 argument");
            }
            Argument<Object> argumentType = (Argument<Object>) constructorArguments[0];
            Deserializer<Object> argumentDeserializer = (Deserializer<Object>) context.findDeserializer(argumentType);

            return (decoder, context1, type1) -> {
                Object v = argumentDeserializer.deserialize(decoder, context1, argumentType);
                try {
                    return (E) deserializableIntrospection.instantiate(v);
                } catch (IllegalArgumentException e) {
                    if (v instanceof String str) {
                        try {
                            return (E) deserializableIntrospection.instantiate(str.toUpperCase(Locale.ENGLISH));
                        } catch (IllegalArgumentException ex) {
                            // throw original
                            throw e;
                        }
                    } else {
                        // throw original
                        throw e;
                    }
                }
            };
        } catch (IntrospectionException | SerdeException e) {
            return this;
        }
    }

    @Override
    public Serializer<E> createSpecific(EncoderContext context, Argument<? extends E> type) throws SerdeException {
        try {
            BeanIntrospection<? extends E> si = introspections.getSerializableIntrospection(type);
            Collection<? extends BeanMethod<? extends E, Object>> beanMethods = si.getBeanMethods();
            for (BeanMethod<? extends E, Object> beanMethod : beanMethods) {
                if (beanMethod.getAnnotationMetadata().hasDeclaredAnnotation(SerdeConfig.SerValue.class)) {
                    Argument<Object> valueType = beanMethod.getReturnType().asArgument();
                    Serializer<? super Object> valueSerializer = context.findSerializer(valueType);
                    return (encoder, subContext, subType, value) -> {
                        @SuppressWarnings("unchecked") Object result = ((Executable) beanMethod).invoke(value);
                        valueSerializer.serialize(encoder, subContext, subType, result);
                    };
                }
            }
            return this;
        } catch (IntrospectionException e) {
            return this;
        }
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends E> type, E value) throws IOException {
        encoder.encodeString(value.name());
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
        while (arrayDecoder.hasNextArrayValue()) {
            set.add(
                Enum.valueOf(generic.getType(), arrayDecoder.decodeString())
            );
        }
        arrayDecoder.finishStructure();
        return EnumSet.copyOf(set);
    }
}
