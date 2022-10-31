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
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.util.CustomizableDeserializer;
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
     *
     * @return The string deserializer
     */
    @Singleton
    @NonNull
    protected Deserializer<String> stringDeserializer() {
        return new StringDeserializer();
    }

    /**
     * Deserializes array lists.
     *
     * @param <E> The element type
     * @return the array list deserializer, never {@code null}
     */
    @Singleton
    @Order(-100) // prioritize over hashset
    @NonNull
    protected <E> Deserializer<ArrayList<E>> arrayListDeserializer() {
        return new ArrayListCollectionDeserializer<>();
    }

    /**
     * Deserializes array deque.
     *
     * @param <E> The element type
     * @return the array list deserializer, never {@code null}
     */
    @Singleton
    @Order(-99) // prioritize over hashset
    @NonNull
    protected <E> Deserializer<ArrayDeque<E>> arrayDequeDeserializer() {
        return new ArrayDequeDeserializer<>();
    }

    /**
     * Deserializes linked lists.
     *
     * @param <E> The element type
     * @return the array list deserializer, never {@code null}
     */
    @Singleton
    @Order(-99) // prioritize over hashset
    @NonNull
    protected <E> Deserializer<LinkedList<E>> linkedListDeserializer() {
        return new LinkedListDeserializer<>();
    }

    /**
     * Deserializes hash sets.
     *
     * @param <E> The element type
     * @return The hash set deserializer, never null
     */
    @NonNull
    @Singleton
    @Order(-50) // prioritize over enumset
    protected <E> Deserializer<HashSet<E>> hashSetDeserializer() {
        return new HashSetDeserializer<>();
    }

    /**
     * Deserializes default set.
     *
     * @param <E> The element type
     * @return The hash set deserializer, never null
     */
    @NonNull
    @Singleton
    protected <E> Deserializer<? extends Set<E>> defaultSetDeserializer() {
        return new HashSetDeserializer<>();
    }

    /**
     * Deserializes linked hash sets.
     *
     * @param <E> The element type
     * @return The linked hash set deserializer, never null
     */
    @NonNull
    @Singleton
    @Order(-51) // prioritize over hashset
    protected <E> Deserializer<LinkedHashSet<E>> linkedHashSetDeserializer() {
        return new LinkedHashDeserializer<>();
    }

    /**
     * Deserializes linked hash sets.
     *
     * @param <E> The element type
     * @return The linked hash set deserializer, never null
     */
    @NonNull
    @Singleton
    @Order(-52) // prioritize over hashset
    protected <E> Deserializer<TreeSet<E>> treeSetDeserializer() {
        return new TreeSetDeserializer<>();
    }

    /**
     * Deserializes hash maps.
     *
     * @param <K> The key type
     * @param <V> The value type
     * @return The hash map deserializer, never {@code null}
     */
    @Singleton
    @NonNull
    @Order(1001)
    protected <K, V> Deserializer<LinkedHashMap<K, V>> linkedHashMapDeserializer() {
        return new LinkedHashMapDeserializer<>();
    }

    /**
     * Deserializes hash maps.
     *
     * @param <K> The key type
     * @param <V> The value type
     * @return The hash map deserializer, never {@code null}
     */
    @Singleton
    @NonNull
    @Order(1002) // prioritize over linked hash map
    protected <K, V> Deserializer<TreeMap<K, V>> treeMapDeserializer() {
        return new TreeMapDeserializer<>();
    }

    /**
     * Deserializes optional values.
     *
     * @param <V> The optional type
     * @return The optional deserializer, never {@code null}
     */
    @Singleton
    @NonNull
    protected <V> Deserializer<Optional<V>> optionalDeserializer() {
        return new OptionalDeserializer<>();
    }

    private static class StringDeserializer implements Deserializer<String> {

        @Override
        public String deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super String> type) throws IOException {
            if (decoder.decodeNull()) {
                return null;
            }
            return decoder.decodeString();
        }

        @Override
        public boolean allowNull() {
            return true;
        }
    }

    private static class OptionalDeserializer<V> implements CustomizableDeserializer<Optional<V>> {

        @Override
        public Deserializer<Optional<V>> createSpecific(DecoderContext context, Argument<? super Optional<V>> type) throws SerdeException {
            @SuppressWarnings("unchecked") final Argument<V> generic =
                    (Argument<V>) type.getFirstTypeVariable().orElse(null);
            if (generic == null) {
                throw new SerdeException("Cannot deserialize raw optional");
            }
            final Deserializer<? extends V> deserializer = context.findDeserializer(generic)
                    .createSpecific(context, generic);

            return new Deserializer<Optional<V>>() {

                @Override
                public Optional<V> deserialize(Decoder decoder, DecoderContext context, Argument<? super Optional<V>> type)
                        throws IOException {
                    if (decoder.decodeNull()) {
                        return Optional.empty();
                    } else {
                        return Optional.ofNullable(
                                deserializer.deserialize(
                                        decoder,
                                        context,
                                        generic
                                )
                        );
                    }
                }

                @Override
                public Optional<V> getDefaultValue(DecoderContext context, Argument<? super Optional<V>> type) {
                    return Optional.empty();
                }
            };
        }
    }

    private static class LinkedHashMapDeserializer<K, V> extends SpecificOnlyMapDeserializer<K, V, LinkedHashMap<K, V>> {
        @Override
        public LinkedHashMap<K, V> getDefaultValue() {
            return new LinkedHashMap<>();
        }
    }

    private static class TreeMapDeserializer<K, V> extends SpecificOnlyMapDeserializer<K, V, TreeMap<K, V>> {
        @Override
        public TreeMap<K, V> getDefaultValue() {
            return new TreeMap<>();
        }
    }

    private abstract static class SpecificOnlyMapDeserializer<K, V, M extends Map<K, V>> implements CustomizableDeserializer<M> {

        @Override
        public Deserializer<M> createSpecific(DecoderContext context, Argument<? super M> type) throws SerdeException {
            final Argument<?>[] generics = type.getTypeParameters();
            if (generics.length == 2) {
                @SuppressWarnings("unchecked") final Argument<K> keyType = (Argument<K>) generics[0];
                @SuppressWarnings("unchecked") final Argument<V> valueType = (Argument<V>) generics[1];
                final Deserializer<? extends V> valueDeser = valueType.equalsType(Argument.OBJECT_ARGUMENT) ? null : context.findDeserializer(valueType)
                        .createSpecific(context, valueType);
                return new Deserializer<M>() {

                    @Override
                    public M deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super M> type) throws IOException {
                        if (decoder.decodeNull()) {
                            return null;
                        }
                        final Decoder objectDecoder = decoder.decodeObject(type);
                        String key = objectDecoder.decodeKey();
                        M map = getDefaultValue(decoderContext, type);
                        ConversionService conversionService = decoderContext.getConversionService();
                        while (key != null) {
                            K k;
                            if (keyType.isInstance(key)) {
                                k = (K) key;
                            } else {
                                try {
                                    k = conversionService.convertRequired(key, keyType);
                                } catch (ConversionErrorException e) {
                                    throw new SerdeException("Error converting Map key [" + key + "] to target type [" + keyType + "]: " + e.getMessage(), e);
                                }
                            }
                            if (valueDeser == null) {
                                map.put(k, (V) objectDecoder.decodeArbitrary());
                            } else {
                                map.put(k, valueDeser.deserialize(objectDecoder, decoderContext, valueType));
                            }
                            key = objectDecoder.decodeKey();
                        }
                        objectDecoder.finishStructure();
                        return map;
                    }

                    @Override
                    public boolean allowNull() {
                        return true;
                    }

                    @Override
                    public M getDefaultValue(DecoderContext context, Argument<? super M> type) {
                        return SpecificOnlyMapDeserializer.this.getDefaultValue();
                    }
                };
            }
            return new Deserializer<M>() {

                @Override
                public M deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super M> type) throws IOException {
                    if (decoder.decodeNull()) {
                        return null;
                    }
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
                }

                @Override
                public M getDefaultValue(DecoderContext context, Argument<? super M> type) {
                    return SpecificOnlyMapDeserializer.this.getDefaultValue();
                }
            };
        }

        @NonNull
        abstract M getDefaultValue();

    }

    private static class ArrayDequeDeserializer<E> extends SpecificOnlyCollectionDeserializer<E, ArrayDeque<E>> {
        @Override
        public ArrayDeque<E> getDefaultValue() {
            return new ArrayDeque<>();
        }
    }

    private static class LinkedListDeserializer<E> extends SpecificOnlyCollectionDeserializer<E, LinkedList<E>> {
        @Override
        public LinkedList<E> getDefaultValue() {
            return new LinkedList<>();
        }
    }

    private static class HashSetDeserializer<E> extends SpecificOnlyCollectionDeserializer<E, HashSet<E>> {
        @Override
        public HashSet<E> getDefaultValue() {
            return new HashSet<>();
        }
    }

    private static class LinkedHashDeserializer<E> extends SpecificOnlyCollectionDeserializer<E, LinkedHashSet<E>> {
        @Override
        public LinkedHashSet<E> getDefaultValue() {
            return new LinkedHashSet<>();
        }
    }

    private static class TreeSetDeserializer<E> extends SpecificOnlyCollectionDeserializer<E, TreeSet<E>> {
        @Override
        public TreeSet<E> getDefaultValue() {
            return new TreeSet<>();
        }
    }

    private static class ArrayListCollectionDeserializer<E> extends SpecificOnlyCollectionDeserializer<E, ArrayList<E>> {
        @Override
        public ArrayList<E> getDefaultValue() {
            return new ArrayList<>();
        }
    }

    private abstract static class SpecificOnlyCollectionDeserializer<E, C extends Collection<E>> implements CustomizableDeserializer<C> {

        @Override
        public Deserializer<C> createSpecific(DecoderContext context, Argument<? super C> type) throws SerdeException {
            final Argument[] generics = type.getTypeParameters();
            if (ArrayUtils.isEmpty(generics)) {
                throw new SerdeException("Cannot deserialize raw list");
            }
            @SuppressWarnings("unchecked") final Argument<E> collectionItemArgument = (Argument<E>) generics[0];
            final Deserializer<? extends E> valueDeser = context.findDeserializer(collectionItemArgument)
                    .createSpecific(context, collectionItemArgument);

            return new Deserializer<C>() {

                @Override
                public C deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super C> type) throws IOException {
                    if (decoder.decodeNull()) {
                        return null;
                    }
                    final Decoder arrayDecoder = decoder.decodeArray();
                    C collection = getDefaultValue(decoderContext, type);
                    while (arrayDecoder.hasNextArrayValue()) {
                        collection.add(
                                valueDeser.deserialize(
                                        arrayDecoder,
                                        decoderContext,
                                        collectionItemArgument
                                )
                        );
                    }
                    arrayDecoder.finishStructure();
                    return collection;
                }

                @Override
                public boolean allowNull() {
                    return true;
                }

                @Override
                @NonNull
                public C getDefaultValue(DecoderContext context, Argument<? super C> type) {
                    return SpecificOnlyCollectionDeserializer.this.getDefaultValue();
                }

            };
        }

        @NonNull
        abstract C getDefaultValue();
    }

}
