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
import io.micronaut.core.naming.Named;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
    private final DeserBean.DerProperty<T, Object>[] properties;
    @Nullable
    private final Map<String, Integer> nameToPropertiesMapping;
    private final long propertiesMask;
    // hash table with open addressing and linear probing that associates the key with the index
    // similar to JDK maps but with int values
    private final String[] keyTable;
    private final int[] indexTable;
    private final int tableMask;

    private PropertiesBag(BeanIntrospection<T> beanIntrospection,
                          int[] originalNameToPropertiesMapping,
                          DeserBean.DerProperty<T, Object>[] properties,
                          Map<String, Integer> nameToPropertiesMapping) {
        this.beanIntrospection = beanIntrospection;
        this.originalNameToPropertiesMapping = originalNameToPropertiesMapping;
        this.properties = properties;
        this.nameToPropertiesMapping = nameToPropertiesMapping;
        if (properties.length > 0 && properties.length <= 64) {
            this.propertiesMask = -1L >>> (64 - properties.length);
        } else {
            this.propertiesMask = 0;
        }
        Stream<String> propStream = beanIntrospection.getBeanProperties().stream().map(Named::getName);
        if (nameToPropertiesMapping != null) {
            propStream = Stream.concat(propStream, nameToPropertiesMapping.keySet().stream());
        }
        Set<String> props = propStream.collect(Collectors.toSet());
        int tableSize = (props.size() * 2) + 1;
        tableSize = Integer.highestOneBit(tableSize) * 2; // round to next power of two
        tableMask = tableSize - 1;
        this.keyTable = new String[tableSize];
        this.indexTable = new int[keyTable.length];
        for (String prop : props) {
            int tableIndex = ~probe(prop);
            keyTable[tableIndex] = prop;
            indexTable[tableIndex] = propertyIndexOfSlow(prop);
        }
    }

    private int probe(String key) {
        int n = keyTable.length;
        int i = key.hashCode() & tableMask;
        while (true) {
            String candidate = keyTable[i];
            if (candidate == null) {
                return ~i;
            } else if (candidate.equals(key)) {
                return i;
            } else {
                i++;
                if (i == n) {
                    i = 0;
                }
            }
        }
    }

    /**
     * Get the properties in this bag with their property names.
     *
     * @return All properties in this bag
     */
    public List<Map.Entry<String, DeserBean.DerProperty<T, Object>>> getProperties() {
        Stream<AbstractMap.SimpleEntry<String, DeserBean.DerProperty<T, Object>>> originalProperties = Arrays.stream(originalNameToPropertiesMapping)
            .filter(index -> index != -1)
            .mapToObj(index -> {
                DeserBean.DerProperty<T, Object> prop = properties[index];
                if (prop.beanProperty == null) {
                    return null;
                }
                return new AbstractMap.SimpleEntry<>(prop.beanProperty.getName(), prop);
            });
        Stream<AbstractMap.SimpleEntry<String, DeserBean.DerProperty<T, Object>>> mappedByName = nameToPropertiesMapping == null ? Stream.empty() : nameToPropertiesMapping.entrySet()
            .stream()
            .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), properties[e.getValue()]));
        return Stream.concat(originalProperties, mappedByName)
            .collect(Collectors.toList());
    }

    /**
     * Get the properties in this bag.
     *
     * @return All properties in this bag
     */
    public List<DeserBean.DerProperty<T, Object>> getDerProperties() {
        return Collections.unmodifiableList(Arrays.asList(properties));
    }

    public int propertyIndexOf(@NonNull String name) {
        int i = probe(name);
        return i < 0 ? -1 : indexTable[i];
    }

    private int propertyIndexOfSlow(@NonNull String name) {
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
        return propertiesMask == 0 ? new ConsumerBig() : new ConsumerSmall();
    }

    /**
     * Properties consumer.
     */
    public abstract sealed class Consumer {
        private Consumer() {
        }

        public boolean isNotConsumed(String name) {
            int propertyIndex = propertyIndexOf(name);
            return propertyIndex != -1 && !isConsumed(propertyIndex);
        }

        public DeserBean.DerProperty<T, Object> findNotConsumed(String name) {
            int propertyIndex = propertyIndexOf(name);
            if (propertyIndex == -1 || isConsumed(propertyIndex)) {
                return null;
            }
            return properties[propertyIndex];
        }

        public DeserBean.DerProperty<T, Object> consume(String name) {
            int propertyIndex = propertyIndexOf(name);
            if (propertyIndex == -1 || isConsumed(propertyIndex)) {
                return null;
            }
            setConsumed(propertyIndex);
            return properties[propertyIndex];
        }

        public List<DeserBean.DerProperty<T, Object>> getNotConsumed() {
            List<DeserBean.DerProperty<T, Object>> list = new ArrayList<>(properties.length);
            int bound = properties.length;
            for (int index = 0; index < bound; index++) {
                if (!isConsumed(index)) {
                    list.add(properties[index]);
                }
            }
            return list;
        }

        abstract boolean isConsumed(int index);

        abstract void setConsumed(int index);

        public abstract boolean isAllConsumed();
    }

    private final class ConsumerBig extends Consumer {
        private final BitSet consumed = new BitSet(properties.length);
        private int remaining = properties.length;

        @Override
        boolean isConsumed(int index) {
            return consumed.get(index);
        }

        @Override
        public boolean isAllConsumed() {
            return remaining == 0;
        }

        @Override
        void setConsumed(int index) {
            consumed.set(index);
            remaining--;
        }
    }

    private final class ConsumerSmall extends Consumer {
        private long consumed = ~propertiesMask;

        @Override
        boolean isConsumed(int index) {
            return (consumed & (1L << index)) != 0;
        }

        @Override
        void setConsumed(int index) {
            consumed |= 1L << index;
        }

        @Override
        public boolean isAllConsumed() {
            return consumed == -1;
        }
    }

    public static class Builder<T> {

        private final BeanIntrospection<T> beanIntrospection;
        private final int[] originalNameToPropertiesMapping;
        @Nullable
        private Map<String, Integer> nameToPropertiesMapping;

        private final List<DeserBean.DerProperty<T, Object>> mutableProperties;

        public Builder(BeanIntrospection<T> beanIntrospection) {
            this(beanIntrospection, beanIntrospection.getBeanProperties().size());
        }

        public Builder(BeanIntrospection<T> beanIntrospection, int expectedPropertiesSize) {
            this.beanIntrospection = beanIntrospection;
            int beanPropertiesSize = beanIntrospection.getBeanProperties().size();
            this.originalNameToPropertiesMapping = new int[beanPropertiesSize];
            Arrays.fill(originalNameToPropertiesMapping, -1);
            this.mutableProperties = new ArrayList<>(expectedPropertiesSize);
        }

        public void register(String name, DeserBean.DerProperty<T, Object> derProperty, boolean addAliases) {
            int newPropertyIndex = mutableProperties.size();
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
            mutableProperties.add(derProperty);

        }

        @Nullable
        public PropertiesBag<T> build() {
            if (mutableProperties.isEmpty()) {
                return null;
            }
            return new PropertiesBag<>(
                beanIntrospection,
                originalNameToPropertiesMapping,
                mutableProperties.toArray(DeserBean.DerProperty[]::new),
                nameToPropertiesMapping
            );
        }

    }

}
