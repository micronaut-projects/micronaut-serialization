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
package io.micronaut.serde.deserializers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Optional;

import io.micronaut.context.annotation.Factory;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.util.NullableDeserializer;
import jakarta.inject.Singleton;

/**
 * Core deserializers.
 */
@Factory
public class CoreDeserializers {

    /**
     * Deserializes string types.
     * @return The string deserializer
     */
    @Singleton
    @NonNull
    protected NullableDeserializer<String> stringDeserializer() {
        return (decoder, decoderContext, type) -> decoder.decodeString();
    }

    /**
     * Deserializes array lists.
     * @param <E> The element type
     * @return the array list deserializer, never {@code null}
     */
    @Singleton
    @Order(-100) // prioritize over hashset
    @NonNull
    protected <E> NullableDeserializer<ArrayList<E>> arrayListDeserializer() {
        return (decoder, decoderContext, type) -> {
            final Argument<?>[] generics = type.getTypeParameters();
            if (ArrayUtils.isEmpty(generics)) {
                throw new SerdeException("Cannot deserialize raw list");
            }
            @SuppressWarnings("unchecked") final Argument<E> generic = (Argument<E>) generics[0];
            final Deserializer<? extends E> valueDeser = decoderContext.findDeserializer(generic);
            final Decoder arrayDecoder = decoder.decodeArray();
            ArrayList<E> list = new ArrayList<>();
            while (arrayDecoder.hasNextArrayValue()) {
                list.add(
                        valueDeser.deserialize(
                                arrayDecoder,
                                decoderContext,
                                generic
                        )
                );
            }
            arrayDecoder.finishStructure();
            return list;
        };
    }

    /**
     * Deserializes hash sets.
     * @param <E> The element type
     * @return The hash set deserializer, never {@link null}
     */
    @NonNull
    @Singleton
    @Order(-50) // prioritize over enumset
    protected <E> NullableDeserializer<HashSet<E>> hashSetDeserializer() {
        return (decoder, decoderContext, type) -> {
            final Argument[] generics = type.getTypeParameters();
            if (ArrayUtils.isEmpty(generics)) {
                throw new SerdeException("Cannot deserialize raw list");
            }
            @SuppressWarnings("unchecked") final Argument<E> generic = (Argument<E>) generics[0];
            final Deserializer<? extends E> valueDeser = decoderContext.findDeserializer(generic);
            final Decoder arrayDecoder = decoder.decodeArray();
            HashSet<E> set = new HashSet<>();
            while (arrayDecoder.hasNextArrayValue()) {
                set.add(
                        valueDeser.deserialize(
                                arrayDecoder,
                                decoderContext,
                                generic
                        )
                );
            }
            arrayDecoder.finishStructure();
            return set;
        };
    }

    /**
     * Deserializes hash maps.
     * @param <V> The value type
     * @return The hash map deserializer, never {@code null}
     */
    @Singleton
    @NonNull
    protected <V> NullableDeserializer<LinkedHashMap<String, V>> linkedHashMapDeserializer() {
        return (decoder, decoderContext, type) -> {
            final Argument<?>[] generics = type.getTypeParameters();
            if (ArrayUtils.isEmpty(generics) && generics.length != 2) {
                throw new SerdeException("Cannot deserialize raw map");
            }
            @SuppressWarnings("unchecked") final Argument<V> generic = (Argument<V>) generics[1];
            final Deserializer<? extends V> valueDeser = decoderContext.findDeserializer(generic);
            final Decoder objectDecoder = decoder.decodeObject();
            String key = objectDecoder.decodeKey();
            LinkedHashMap<String, V> map = new LinkedHashMap<>();
            while (key != null) {
                map.put(key, valueDeser.deserialize(
                                objectDecoder,
                                decoderContext,
                                generic
                        )
                );
                key = objectDecoder.decodeKey();
            }
            objectDecoder.finishStructure();
            return map;
        };
    }

    /**
     * Deserializes optional values.
     * @param <V> The optional type
     * @return The optional deserializer, never {@code null}
     */
    @Singleton
    @NonNull
    protected <V> Deserializer<Optional<V>> optionalDeserializer() {
        return new Deserializer<Optional<V>>() {
            @Override
            public Optional<V> deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Optional<V>> type)
                    throws IOException {
                @SuppressWarnings("unchecked") final Argument<V> generic =
                        (Argument<V>) type.getFirstTypeVariable().orElse(null);
                if (generic == null) {
                    throw new SerdeException("Cannot deserialize raw optional");
                }
                final Deserializer<? extends V> deserializer = decoderContext.findDeserializer(generic);

                if (decoder.decodeNull()) {
                    return Optional.empty();
                } else {
                    return Optional.ofNullable(
                            deserializer.deserialize(
                                    decoder,
                                    decoderContext,
                                    generic
                            )
                    );
                }
            }

            @Override
            public Optional<V> getDefaultValue() {
                return Optional.empty();
            }
        };
    }
}
