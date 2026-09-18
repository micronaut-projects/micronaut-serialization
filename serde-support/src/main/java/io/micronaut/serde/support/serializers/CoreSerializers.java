/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.serde.support.serializers;

import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.config.SerializationConfiguration;
import io.micronaut.serde.support.SerializerRegistrar;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Core serializers.
 */
@Internal
public final class CoreSerializers {

    public static void register(SerializationConfiguration serializationConfiguration, Consumer<SerializerRegistrar<?>> consumer) {
        consumer.accept(new CustomizedMapSerializer<>());
        consumer.accept(new IterableSerializer<>());
        consumer.accept(new OptionalMultiValuesSerializer<>(serializationConfiguration));
        consumer.accept(new OptionalValuesSerializer<>());
        consumer.accept(new StreamSerializer<>());

        // Register specific collection serializers so that a user-defined
        // Serializer<List<Foo>> does not accidentally match List<Bar> or other unrelated
        // collection types (e.g. raw/unparameterized list types).
        // See https://github.com/micronaut-projects/micronaut-serialization/issues/1187
        consumer.accept(new SpecificOnlyCollectionSerializer<Object, ArrayList<Object>>(ArrayList.class) { });
        consumer.accept(new SpecificOnlyCollectionSerializer<Object, LinkedList<Object>>(LinkedList.class) { });
        consumer.accept(new SpecificOnlyCollectionSerializer<Object, HashSet<Object>>(HashSet.class) { });
        consumer.accept(new SpecificOnlyCollectionSerializer<Object, LinkedHashSet<Object>>(LinkedHashSet.class) { });
        consumer.accept(new SpecificOnlyCollectionSerializer<Object, TreeSet<Object>>(TreeSet.class) { });
        consumer.accept(new SpecificOnlyCollectionSerializer<Object, List<Object>>(List.class) { });
        consumer.accept(new SpecificOnlyCollectionSerializer<Object, Collection<Object>>(Collection.class) { });
    }

}
