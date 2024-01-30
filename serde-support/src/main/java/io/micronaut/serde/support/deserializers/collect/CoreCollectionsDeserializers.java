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
package io.micronaut.serde.support.deserializers.collect;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Order;
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
@BootstrapContextCompatible
public class CoreCollectionsDeserializers {

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
        return new SpecificOnlyCollectionDeserializer<>() {

            @Override
            protected Deserializer<ArrayList<E>> createSpecific(Argument<? super ArrayList<E>> collectionArgument,
                                                                Argument<E> collectionItemArgument,
                                                                Deserializer<? extends E> valueDeser) {
                if (collectionArgument.getType().isAssignableFrom(ArrayList.class) && collectionItemArgument.getType().equals(String.class)) {
                    return (Deserializer) StringListDeserializer.INSTANCE;
                }
                return new ArrayListDeserializer<>(valueDeser, collectionItemArgument);
            }
        };
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
        return new SpecificOnlyCollectionDeserializer<>() {

            @Override
            protected Deserializer<ArrayDeque<E>> createSpecific(Argument<? super ArrayDeque<E>> collectionArgument,
                                                                 Argument<E> collectionItemArgument,
                                                                 Deserializer<? extends E> valueDeser) {
                return new ArrayDequeDeserializer<>(valueDeser, collectionItemArgument);
            }
        };
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
        return new SpecificOnlyCollectionDeserializer<>() {

            @Override
            protected Deserializer<LinkedList<E>> createSpecific(Argument<? super LinkedList<E>> collectionArgument,
                                                                 Argument<E> collectionItemArgument,
                                                                 Deserializer<? extends E> valueDeser) {
                return new LinkedListDeserializer<>(valueDeser, collectionItemArgument);
            }
        };
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
        return new SpecificOnlyCollectionDeserializer<>() {

            @Override
            protected Deserializer<HashSet<E>> createSpecific(Argument<? super HashSet<E>> collectionArgument,
                                                              Argument<E> collectionItemArgument,
                                                              Deserializer<? extends E> valueDeser) {
                return new HashSetDeserializer<>(valueDeser, collectionItemArgument);
            }
        };
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
        return hashSetDeserializer();
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
        return new SpecificOnlyCollectionDeserializer<>() {

            @Override
            protected Deserializer<LinkedHashSet<E>> createSpecific(Argument<? super LinkedHashSet<E>> collectionArgument,
                                                                    Argument<E> collectionItemArgument,
                                                                    Deserializer<? extends E> valueDeser) {
                return new LinkedHashSetDeserializer<>(valueDeser, collectionItemArgument);
            }
        };
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
        return new SpecificOnlyCollectionDeserializer<>() {

            @Override
            protected Deserializer<TreeSet<E>> createSpecific(Argument<? super TreeSet<E>> collectionArgument,
                                                              Argument<E> collectionItemArgument,
                                                              Deserializer<? extends E> valueDeser) {
                return new TreeSetDeserializer<>(valueDeser, collectionItemArgument);
            }
        };
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
        return new SpecificOnlyMapDeserializer<>() {

            @Override
            protected Deserializer<LinkedHashMap<K, V>> createSpecific(Argument<K> keyType, Argument<V> valueType, Deserializer<? extends V> valueDeser) {
                return new LinkedHashMapDeserializer<>(valueDeser, keyType, valueType);
            }
        };
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
        return new SpecificOnlyMapDeserializer<>() {

            @Override
            protected Deserializer<TreeMap<K, V>> createSpecific(Argument<K> keyType, Argument<V> valueType, Deserializer<? extends V> valueDeser) {
                return new TreeMapDeserializer<>(valueDeser, keyType, valueType);
            }
        };
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

            return new Deserializer<>() {

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
                public Optional<V> deserializeNullable(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super Optional<V>> type) throws IOException {
                    return deserialize(decoder, context, type);
                }

                @Override
                public Optional<V> getDefaultValue(DecoderContext context, Argument<? super Optional<V>> type) {
                    return Optional.empty();
                }
            };
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
                return createSpecific(keyType, valueType, valueDeser);
            }
            return new Deserializer<>() {

                @Override
                public M deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super M> type) throws IOException {
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

            };
        }

        @NonNull
        protected abstract Deserializer<M> createSpecific(Argument<K> keyType, Argument<V> valueType, Deserializer<? extends V> valueDeser);

    }

    private abstract static class SpecificOnlyCollectionDeserializer<E, C extends Collection<E>> implements CustomizableDeserializer<C> {

        @Override
        public Deserializer<C> createSpecific(DecoderContext context, Argument<? super C> type) throws SerdeException {
            final Argument<?>[] generics = type.getTypeParameters();
            if (ArrayUtils.isEmpty(generics)) {
                throw new SerdeException("Cannot deserialize raw list");
            }
            @SuppressWarnings("unchecked") final Argument<E> collectionItemArgument = (Argument<E>) generics[0];
            final Deserializer<? extends E> valueDeser = context.findDeserializer(collectionItemArgument)
                .createSpecific(context, collectionItemArgument);

            return createSpecific(type, collectionItemArgument, valueDeser);
        }

        protected abstract Deserializer<C> createSpecific(Argument<? super C> collectionArgument,
                                                          Argument<E> collectionItemArgument,
                                                          Deserializer<? extends E> valueDeser);

    }

}
