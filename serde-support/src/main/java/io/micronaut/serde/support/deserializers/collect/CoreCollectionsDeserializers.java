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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.support.DeserializerRegistrar;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Core collections deserializers.
 */
@Internal
public final class CoreCollectionsDeserializers {

    private static final int PREFERRED_COLLECTION_OR_LIST_ORDER = -100;
    private static final int PREFERRED_DEQUE_ORDER = -99;
    private static final int PREFERRED_SET_ORDER = -98;
    private static final int PREFERRED_MAP_ORDER = -97;

    public static void register(ConversionService conversionService, Consumer<DeserializerRegistrar<?>> consumer) {
        consumer.accept(new StringListDeserializer());
        consumer.accept(new SpecificOnlyCollectionDeserializer<Object, ArrayList<Object>>(ArrayList.class) {

            @Override
            protected Deserializer<ArrayList<Object>> createSpecific(Argument<? super ArrayList<Object>> collectionArgument, Argument<Object> collectionItemArgument, Deserializer<?> valueDeser) {
                return new ArrayListDeserializer<>(valueDeser, collectionItemArgument);
            }

            @Override
            public int getOrder() {
                return PREFERRED_COLLECTION_OR_LIST_ORDER;
            }
        });
        consumer.accept(new SpecificOnlyCollectionDeserializer<Object, ArrayDeque<Object>>(ArrayDeque.class) {

            @Override
            protected Deserializer<ArrayDeque<Object>> createSpecific(Argument<? super ArrayDeque<Object>> collectionArgument, Argument<Object> collectionItemArgument, Deserializer<?> valueDeser) {
                return new ArrayDequeDeserializer<>(valueDeser, collectionItemArgument);
            }

            @Override
            public int getOrder() {
                return PREFERRED_DEQUE_ORDER;
            }
        });
        consumer.accept(new SpecificOnlyCollectionDeserializer<Object, LinkedList<Object>>(LinkedList.class) {

            @Override
            protected Deserializer<LinkedList<Object>> createSpecific(Argument<? super LinkedList<Object>> collectionArgument, Argument<Object> collectionItemArgument, Deserializer<?> valueDeser) {
                return new LinkedListDeserializer<>(valueDeser, collectionItemArgument);
            }

        });
        consumer.accept(new SpecificOnlyCollectionDeserializer<Object, HashSet<Object>>(HashSet.class) {

            @Override
            protected Deserializer<HashSet<Object>> createSpecific(Argument<? super HashSet<Object>> collectionArgument, Argument<Object> collectionItemArgument, Deserializer<?> valueDeser) {
                return new HashSetDeserializer<>(valueDeser, collectionItemArgument);
            }

        });
        consumer.accept(new SpecificOnlyCollectionDeserializer<Object, LinkedHashSet<Object>>(LinkedHashSet.class) {

            @Override
            protected Deserializer<LinkedHashSet<Object>> createSpecific(Argument<? super LinkedHashSet<Object>> collectionArgument, Argument<Object> collectionItemArgument, Deserializer<?> valueDeser) {
                return new LinkedHashSetDeserializer<>(valueDeser, collectionItemArgument);
            }

            @Override
            public int getOrder() {
                return PREFERRED_SET_ORDER;
            }
        });
        consumer.accept(new SpecificOnlyCollectionDeserializer<Object, TreeSet<Object>>(TreeSet.class) {

            @Override
            protected Deserializer<TreeSet<Object>> createSpecific(Argument<? super TreeSet<Object>> collectionArgument, Argument<Object> collectionItemArgument, Deserializer<?> valueDeser) {
                return new TreeSetDeserializer<>(valueDeser, collectionItemArgument);
            }

        });
        consumer.accept(new SpecificOnlyMapDeserializer<Object, Object, HashMap<Object, Object>>(HashMap.class) {

            @Override
            protected Deserializer<HashMap<Object, Object>> createSpecific(Argument<Object> keyType, Argument<Object> valueType, Deserializer<?> valueDeser) {
                return new HashMapDeserializer<>(valueDeser, keyType, valueType);
            }

        });
        consumer.accept(new SpecificOnlyMapDeserializer<Object, Object, LinkedHashMap<Object, Object>>(LinkedHashMap.class) {

            @Override
            protected Deserializer<LinkedHashMap<Object, Object>> createSpecific(Argument<Object> keyType, Argument<Object> valueType, Deserializer<?> valueDeser) {
                return new LinkedHashMapDeserializer<>(valueDeser, keyType, valueType);
            }

            @Override
            public int getOrder() {
                return PREFERRED_MAP_ORDER;
            }

        });
        consumer.accept(new SpecificOnlyMapDeserializer<Object, Object, TreeMap<Object, Object>>(TreeMap.class) {

            @Override
            protected Deserializer<TreeMap<Object, Object>> createSpecific(Argument<Object> keyType, Argument<Object> valueType, Deserializer<?> valueDeser) {
                return new TreeMapDeserializer<>(valueDeser, keyType, valueType);
            }

        });
        consumer.accept(new EnumSetDeserializer<>());
        consumer.accept(new EnumMapDeserializer<>());
        consumer.accept(new ConvertibleValuesDeserializer(conversionService));
    }

}
