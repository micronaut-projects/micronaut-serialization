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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * The collection of properties. Some operations are delegating to {@link BeanIntrospection} property index resolving,
 * which is using compile-time string switch instead of map.
 *
 * @param <T> The bean type
 * @author Denis Stepanov
 * @since 1.0.0
 */
final class PropertiesBag<T> {

    private final BeanIntrospection<T> beanIntrospection;
    private final int[] originalNameToPropertiesMapping;
    private List<DeserBean.DerProperty<T, Object>> properties;
    @Nullable
    private Map<String, Integer> nameToPropertiesMapping;

    public PropertiesBag(BeanIntrospection<T> beanIntrospection) {
        this(beanIntrospection, beanIntrospection.getBeanProperties().size());
    }

    public PropertiesBag(BeanIntrospection<T> beanIntrospection, int expectedPropertiesSize) {
        this.beanIntrospection = beanIntrospection;
        int beanPropertiesSize = beanIntrospection.getBeanProperties().size();
        this.originalNameToPropertiesMapping = new int[beanPropertiesSize];
        Arrays.fill(originalNameToPropertiesMapping, -1);
        this.properties = new ArrayList<>(expectedPropertiesSize);
    }

    public void register(String name, DeserBean.DerProperty<T, Object> derProperty, boolean addAliases) {
        int newPropertyIndex = properties.size();
        if (derProperty.beanProperty != null && derProperty.beanProperty.getDeclaringBean() == beanIntrospection && name.equals(derProperty.beanProperty.getName())) {
            originalNameToPropertiesMapping[beanIntrospection.propertyIndexOf(name)] = newPropertyIndex;
        } else {
            if (nameToPropertiesMapping == null) {
                nameToPropertiesMapping = new HashMap<>();
            }
            nameToPropertiesMapping.put(name, newPropertyIndex);
        }
        if (addAliases && derProperty.aliases != null && derProperty.aliases.length > 0) {
            if (nameToPropertiesMapping == null) {
                nameToPropertiesMapping = new HashMap<>();
            }
            for (String alias : derProperty.aliases) {
                nameToPropertiesMapping.put(alias, newPropertyIndex);
            }
        }
        properties.add(derProperty);
    }

    public void seal() {
        ((ArrayList) properties).trimToSize();
        properties = Collections.unmodifiableList(properties);
    }

    public List<Map.Entry<String, DeserBean.DerProperty<T, Object>>> getProperties() {
        Stream<AbstractMap.SimpleEntry<String, DeserBean.DerProperty<T, Object>>> originalProperties = Arrays.stream(originalNameToPropertiesMapping)
                .filter(index -> index != -1)
                .mapToObj(index -> {
                    DeserBean.DerProperty<T, Object> prop = properties.get(index);
                    if (prop.beanProperty == null) {
                        return null;
                    }
                    return new AbstractMap.SimpleEntry<>(prop.beanProperty.getName(), prop);
                });
        Stream<AbstractMap.SimpleEntry<String, DeserBean.DerProperty<T, Object>>> mappedByName = nameToPropertiesMapping == null ? Stream.empty() : nameToPropertiesMapping.entrySet()
                .stream()
                .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), properties.get(e.getValue())));
        return Stream.concat(originalProperties, mappedByName)
                .collect(Collectors.toList());
    }

    public int propertyIndexOf(@NonNull String name) {
        int propertyIndex = -1;
        int beanPropertyIndex = beanIntrospection.propertyIndexOf(name);
        if (beanPropertyIndex != -1) {
            propertyIndex = originalNameToPropertiesMapping[beanPropertyIndex];
        }
        if (propertyIndex != -1) {
            return propertyIndex;
        }
        return nameToPropertiesMapping == null ? -1 : nameToPropertiesMapping.getOrDefault(name, -1);
    }

    public Consumer newConsumer() {
        return new Consumer();
    }

    /**
     * Properties consumer.
     */
    public final class Consumer {

        private final BitSet consumedSet = new BitSet(properties.size());
        private int remaining = properties.size();

        public boolean isNotConsumed(String name) {
            int propertyIndex = propertyIndexOf(name);
            return propertyIndex != -1 && !consumedSet.get(propertyIndex);
        }

        public DeserBean.DerProperty<T, ?> findNotConsumed(String name) {
            int propertyIndex = propertyIndexOf(name);
            if (propertyIndex == -1 || consumedSet.get(propertyIndex)) {
                return null;
            }
            return properties.get(propertyIndex);
        }

        public DeserBean.DerProperty<T, ?> consume(String name) {
            int propertyIndex = propertyIndexOf(name);
            if (propertyIndex == -1 || consumedSet.get(propertyIndex)) {
                return null;
            }
            consumedSet.set(propertyIndex);
            remaining--;
            return properties.get(propertyIndex);
        }

        public List<DeserBean.DerProperty<T, Object>> getNotConsumed() {
            return IntStream.range(0, properties.size())
                    .filter(index -> !consumedSet.get(index))
                    .mapToObj(index -> properties.get(index))
                    .collect(Collectors.toList());
        }

        public boolean isAllConsumed() {
            return remaining == 0;
        }

    }

}
