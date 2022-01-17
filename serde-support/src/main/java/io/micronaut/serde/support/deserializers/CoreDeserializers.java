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
package io.micronaut.serde.support.deserializers;

import io.micronaut.context.annotation.Factory;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.util.NullableDeserializer;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

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
        return (CollectionNullableDeserializer<E, ArrayList<E>>) ArrayList::new;
    }

    /**
     * Deserializes array deque.
     * @param <E> The element type
     * @return the array list deserializer, never {@code null}
     */
    @Singleton
    @Order(-99) // prioritize over hashset
    @NonNull
    protected <E> NullableDeserializer<ArrayDeque<E>> arrayDequeDeserializer() {
        return (CollectionNullableDeserializer<E, ArrayDeque<E>>) ArrayDeque::new;
    }

    /**
     * Deserializes linked lists.
     * @param <E> The element type
     * @return the array list deserializer, never {@code null}
     */
    @Singleton
    @Order(-99) // prioritize over hashset
    @NonNull
    protected <E> NullableDeserializer<LinkedList<E>> linkedListDeserializer() {
        return (CollectionNullableDeserializer<E, LinkedList<E>>) LinkedList::new;
    }

    /**
     * Deserializes hash sets.
     * @param <E> The element type
     * @return The hash set deserializer, never null
     */
    @NonNull
    @Singleton
    @Order(-50) // prioritize over enumset
    protected <E> NullableDeserializer<HashSet<E>> hashSetDeserializer() {
        return (CollectionNullableDeserializer<E, HashSet<E>>) HashSet::new;
    }

    /**
     * Deserializes default set.
     * @param <E> The element type
     * @return The hash set deserializer, never null
     */
    @NonNull
    @Singleton
    protected <E> NullableDeserializer<Set<E>> defaultSetDeserializer() {
        return (CollectionNullableDeserializer<E, Set<E>>) HashSet::new;
    }

    /**
     * Deserializes linked hash sets.
     * @param <E> The element type
     * @return The linked hash set deserializer, never null
     */
    @NonNull
    @Singleton
    @Order(-51) // prioritize over hashset
    protected <E> NullableDeserializer<LinkedHashSet<E>> linkedHashSetDeserializer() {
        return (CollectionNullableDeserializer<E, LinkedHashSet<E>>) LinkedHashSet::new;
    }

    /**
     * Deserializes linked hash sets.
     * @param <E> The element type
     * @return The linked hash set deserializer, never null
     */
    @NonNull
    @Singleton
    @Order(-52) // prioritize over hashset
    protected <E> NullableDeserializer<TreeSet<E>> treeSetDeserializer() {
        return (CollectionNullableDeserializer<E, TreeSet<E>>) TreeSet::new;
    }

    /**
     * Deserializes hash maps.
     * @param <K> The key type
     * @param <V> The value type
     * @return The hash map deserializer, never {@code null}
     */
    @Singleton
    @NonNull
    @Order(1001)
    protected <K, V> NullableDeserializer<LinkedHashMap<K, V>> linkedHashMapDeserializer() {
        return (MapNullableDeserializer<K, V, LinkedHashMap<K, V>>) LinkedHashMap::new;
    }

    /**
     * Deserializes hash maps.
     * @param <K> The key type
     * @param <V> The value type
     * @return The hash map deserializer, never {@code null}
     */
    @Singleton
    @NonNull
    @Order(1002) // prioritize over linked hash map
    protected <K, V> NullableDeserializer<TreeMap<K, V>> treeMapDeserializer() {
        return (MapNullableDeserializer<K, V, TreeMap<K, V>>) TreeMap::new;
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
            public Optional<V> getDefaultValue(DecoderContext decoderContext, Argument<? super Optional<V>> type) {
                return Optional.empty();
            }
        };
    }

    private interface MapNullableDeserializer<K, V, M extends Map<K, V>> extends NullableDeserializer<M> {

        @Override
        default M deserializeNonNull(Decoder decoder, Deserializer.DecoderContext decoderContext, Argument<? super M> type) throws IOException {
            final Argument<?>[] generics = type.getTypeParameters();

            if (ArrayUtils.isEmpty(generics) && generics.length != 2) {
                // raw map
                final Object o = decoder.decodeArbitrary();
                if (type.isInstance(o)) {
                    return (M) o;
                } else if (o instanceof Map) {
                    final M map = getDefaultValue(decoderContext, type);
                    map.putAll((Map) o);
                    return map;
                } else {
                    throw new SerdeException("Cannot deserialize map of type [" + type + "] from value: " + o);
                }
            } else {

                @SuppressWarnings("unchecked")
                final Argument<K> keyType = (Argument<K>) generics[0];
                @SuppressWarnings("unchecked")
                final Argument<V> valueType = (Argument<V>) generics[1];
                final Deserializer<? extends V> valueDeser = valueType.equalsType(Argument.OBJECT_ARGUMENT) ? null : decoderContext.findDeserializer(valueType);
                final Decoder objectDecoder = decoder.decodeObject(type);
                String key = objectDecoder.decodeKey();
                M map = getDefaultValue(decoderContext, type);
                while (key != null) {
                    K k;
                    if (keyType.isInstance(key)) {
                        k = (K) key;
                    } else {
                        try {
                            k = decoderContext.getConversionService().convertRequired(
                                    key,
                                    keyType
                            );
                        } catch (ConversionErrorException e) {
                            throw new SerdeException("Error converting Map key [" + key + "] to target type [" + keyType + "]: " + e.getMessage(), e);
                        }
                    }
                    if (valueDeser == null) {
                        map.put(k, (V) objectDecoder.decodeArbitrary());
                    } else {

                        map.put(k, valueDeser.deserialize(
                                        objectDecoder,
                                        decoderContext,
                                        valueType
                                )
                        );
                    }
                    key = objectDecoder.decodeKey();
                }
                objectDecoder.finishStructure();
                return map;
            }
        }

        @Override
        @NonNull
        default M getDefaultValue(DecoderContext decoderContext, Argument<? super M> type) {
            return getDefaultValue();
        }

        @NonNull
        M getDefaultValue();
    }

    private interface CollectionNullableDeserializer<E, C extends Collection<E>> extends NullableDeserializer<C> {

        @Override
        default C deserializeNonNull(Decoder decoder, Deserializer.DecoderContext decoderContext, Argument<? super C> type) throws IOException {
            final Argument[] generics = type.getTypeParameters();
            if (ArrayUtils.isEmpty(generics)) {
                throw new SerdeException("Cannot deserialize raw list");
            }
            @SuppressWarnings("unchecked") final Argument<E> generic = (Argument<E>) generics[0];
            final Deserializer<? extends E> valueDeser = decoderContext.findDeserializer(generic);
            final Decoder arrayDecoder = decoder.decodeArray();
            C collection = getDefaultValue(decoderContext, type);
            while (arrayDecoder.hasNextArrayValue()) {
                collection.add(
                        valueDeser.deserialize(
                                arrayDecoder,
                                decoderContext,
                                generic
                        )
                );
            }
            arrayDecoder.finishStructure();
            return collection;
        }

        @Override
        default boolean allowNull() {
            return true;
        }

        @Override
        @NonNull
        default C getDefaultValue(DecoderContext decoderContext, Argument<? super C> type) {
            return getDefaultValue();
        }

        @NonNull
        C getDefaultValue();
    }
}
