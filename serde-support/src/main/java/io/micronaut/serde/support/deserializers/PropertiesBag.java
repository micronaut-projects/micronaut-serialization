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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.naming.Named;
import io.micronaut.core.util.StringIntMap;

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
@Internal
final class PropertiesBag<T> {

    private final BeanIntrospection<T> beanIntrospection;
    private final int[] originalNameToPropertiesMapping;
    private final DeserBean.DerProperty<T, Object>[] properties;
    @Nullable
    private final Map<String, Integer> nameToPropertiesMapping;
    private final long propertiesMask;
    private final StringIntMap nameToPosition;

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
        nameToPosition = new StringIntMap(props.size());
        for (String prop : props) {
            nameToPosition.put(prop, propertyIndexOfSlow(prop));
        }
    }

    /**
     * Get the properties in this bag.
     *
     * @return All properties in this bag
     */
    List<DeserBean.DerProperty<T, Object>> getProperties() {
        Stream<DeserBean.DerProperty<T, Object>> originalProperties = Arrays.stream(originalNameToPropertiesMapping)
            .filter(index -> index != -1)
            .mapToObj(index -> {
                DeserBean.DerProperty<T, Object> prop = properties[index];
                if (prop.beanProperty == null) {
                    return null;
                }
                return prop;
            });
        Stream<DeserBean.DerProperty<T, Object>> mappedByName = nameToPropertiesMapping == null ? Stream.empty() : nameToPropertiesMapping.values()
            .stream()
            .map(index -> properties[index]);
        return Stream.concat(originalProperties, mappedByName)
            .toList();
    }

    /**
     * Get the properties in this bag.
     *
     * @return All properties in this bag
     */
    List<DeserBean.DerProperty<T, Object>> getDerProperties() {
        return Collections.unmodifiableList(Arrays.asList(properties));
    }

    DeserBean.DerProperty<T, Object>[] getPropertiesArray() {
        return properties;
    }

    int propertyIndexOf(@NonNull String name) {
        return nameToPosition.get(name, -1);
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

    Consumer newConsumer() {
        return propertiesMask == 0 ? new ConsumerBig() : new ConsumerSmall();
    }

    /**
     * Properties consumer.
     */
    abstract sealed class Consumer {
        private Consumer() {
        }

        public DeserBean.DerProperty<T, Object> consume(String name) {
            int propertyIndex = nameToPosition.get(name, -1);
            if (propertyIndex == -1 || isConsumed(propertyIndex)) {
                return null;
            }
            setConsumed(propertyIndex);
            return properties[propertyIndex];
        }

        public void consume(int propertyIndex) {
            if (propertyIndex == -1 || isConsumed(propertyIndex)) {
                return;
            }
            setConsumed(propertyIndex);
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

    static class Builder<T> {

        private final BeanIntrospection<T> beanIntrospection;
        private final int[] originalNameToPropertiesMapping;
        @Nullable
        private Map<String, Integer> nameToPropertiesMapping;

        private final List<DeserBean.DerProperty<T, Object>> mutableProperties;

        Builder(BeanIntrospection<T> beanIntrospection) {
            this(beanIntrospection, beanIntrospection.getBeanProperties().size());
        }

        Builder(BeanIntrospection<T> beanIntrospection, int expectedPropertiesSize) {
            this.beanIntrospection = beanIntrospection;
            int beanPropertiesSize = beanIntrospection.getBeanProperties().size();
            this.originalNameToPropertiesMapping = new int[beanPropertiesSize];
            Arrays.fill(originalNameToPropertiesMapping, -1);
            this.mutableProperties = new ArrayList<>(expectedPropertiesSize);
        }

        void register(String name, DeserBean.DerProperty<T, Object> derProperty, boolean addAliases) {
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
        PropertiesBag<T> build() {
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
