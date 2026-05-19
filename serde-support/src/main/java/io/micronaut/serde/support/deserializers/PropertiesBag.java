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
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.naming.Named;
import io.micronaut.core.util.StringIntMap;
import io.micronaut.serde.Keys;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
    private final boolean acceptCaseInsensitiveProperties;
    private final long propertiesMask;
    private final StringIntMap nameToPosition;
    @Nullable
    private final StringIntMap caseInsensitiveNameToPosition;
    private final List<String> keys;
    private final Range keyRange;
    private final int[] keyToPropertyIndex;
    private final int @Nullable [] remappedKeyToPropertyIndex;
    private final boolean keyIndexesIdentity;

    private PropertiesBag(BeanIntrospection<T> beanIntrospection,
                          int[] originalNameToPropertiesMapping,
                          DeserBean.DerProperty<T, Object>[] properties,
                          @Nullable Map<String, Integer> nameToPropertiesMapping,
                          boolean acceptCaseInsensitiveProperties,
                          List<String> allKeys) {
        this.beanIntrospection = beanIntrospection;
        this.originalNameToPropertiesMapping = originalNameToPropertiesMapping;
        this.properties = properties;
        this.nameToPropertiesMapping = nameToPropertiesMapping;
        this.acceptCaseInsensitiveProperties = acceptCaseInsensitiveProperties;
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
        if (acceptCaseInsensitiveProperties) {
            caseInsensitiveNameToPosition = new StringIntMap(props.size());
            for (String prop : props) {
                caseInsensitiveNameToPosition.put(prop.toLowerCase(Locale.ROOT), propertyIndexOfSlow(prop));
            }
        } else {
            caseInsensitiveNameToPosition = null;
        }
        KeyIndex keyIndex = buildKeyIndex(allKeys);
        this.keys = keyIndex.keys;
        this.keyRange = keyIndex.range;
        this.keyToPropertyIndex = keyIndex.propertyIndexes;
        this.remappedKeyToPropertyIndex = keyIndex.remappedPropertyIndexes;
        this.keyIndexesIdentity = keyIndex.identity;
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
                return prop.beanProperty == null ? null : prop;
            })
            .filter(Objects::nonNull);
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

    boolean hasIdentityKeyIndexes() {
        return keyIndexesIdentity;
    }

    long propertiesMask() {
        return propertiesMask;
    }

    int propertyIndexOf(String name) {
        int propertyIndex = nameToPosition.get(name, -1);
        if (propertyIndex == -1 && acceptCaseInsensitiveProperties) {
            return Objects.requireNonNull(caseInsensitiveNameToPosition).get(name.toLowerCase(Locale.ROOT), -1);
        }
        return propertyIndex;
    }

    boolean contains(String name) {
        return propertyIndexOf(name) != Keys.UNKNOWN_KEY;
    }

    DeserBean.@Nullable DerProperty<T, Object> property(int keyIndex) {
        int propertyIndex = propertyIndexForKeyIndex(keyIndex);
        return propertyIndex == Keys.UNKNOWN_KEY ? null : properties[propertyIndex];
    }

    boolean containsKeyIndex(int keyIndex) {
        return propertyIndexForKeyIndex(keyIndex) != Keys.UNKNOWN_KEY;
    }

    private int propertyIndexForKeyIndex(int keyIndex) {
        if (keyRange.contains(keyIndex)) {
            return keyToPropertyIndex[keyRange.offset(keyIndex)];
        }
        if (remappedKeyToPropertyIndex != null && keyIndex >= 0 && keyIndex < remappedKeyToPropertyIndex.length) {
            return remappedKeyToPropertyIndex[keyIndex];
        }
        return Keys.UNKNOWN_KEY;
    }

    private KeyIndex buildKeyIndex(List<String> allKeys) {
        int keyCount = beanIntrospection.getBeanProperties().size();
        if (nameToPropertiesMapping != null) {
            keyCount += nameToPropertiesMapping.size();
        }
        ArrayList<String> keys = new ArrayList<>(keyCount);
        int[] localKeyToPropertyIndex = new int[keyCount];
        HashSet<String> seenKeys = new HashSet<>(keyCount);
        int beanPropertyIndex = 0;
        for (Named beanProperty : beanIntrospection.getBeanProperties()) {
            int propertyIndex = originalNameToPropertiesMapping[beanPropertyIndex++];
            if (propertyIndex != -1) {
                addLocalKey(keys, localKeyToPropertyIndex, seenKeys, beanProperty.getName(), propertyIndex);
            }
        }
        if (nameToPropertiesMapping != null) {
            for (Map.Entry<String, Integer> entry : nameToPropertiesMapping.entrySet()) {
                addLocalKey(keys, localKeyToPropertyIndex, seenKeys, entry.getKey(), entry.getValue());
            }
        }
        int localKeyCount = keys.size();
        int keyRangeStart = allKeys.size();
        int[] rangePropertyIndexes = new int[localKeyCount];
        int[] aggregateKeyIndexes = new int[localKeyCount];
        boolean remapped = false;
        int rangeSize = 0;
        for (int i = 0; i < localKeyCount; i++) {
            int aggregateKeyIndex = addKey(allKeys, keys.get(i), acceptCaseInsensitiveProperties);
            aggregateKeyIndexes[i] = aggregateKeyIndex;
            if (aggregateKeyIndex == keyRangeStart + rangeSize) {
                rangePropertyIndexes[rangeSize++] = localKeyToPropertyIndex[i];
            } else {
                remapped = true;
            }
        }
        int[] propertyIndexes = rangeSize == rangePropertyIndexes.length ? rangePropertyIndexes : Arrays.copyOf(rangePropertyIndexes, rangeSize);
        int[] remappedPropertyIndexes = null;
        if (remapped) {
            remappedPropertyIndexes = new int[allKeys.size()];
            Arrays.fill(remappedPropertyIndexes, Keys.UNKNOWN_KEY);
            for (int i = 0; i < localKeyCount; i++) {
                remappedPropertyIndexes[aggregateKeyIndexes[i]] = localKeyToPropertyIndex[i];
            }
        }
        Range keyRange = new Range(keyRangeStart, keyRangeStart + rangeSize);
        return new KeyIndex(
            Collections.unmodifiableList(keys),
            keyRange,
            propertyIndexes,
            remappedPropertyIndexes,
            !remapped && keyRangeStart == 0 && isIdentity(propertyIndexes)
        );
    }

    private void addLocalKey(List<String> keys, int[] keyToPropertyIndex, Set<String> seenKeys, String key, int propertyIndex) {
        if (seenKeys.add(normalize(key, acceptCaseInsensitiveProperties))) {
            int keyIndex = keys.size();
            keys.add(key);
            keyToPropertyIndex[keyIndex] = propertyIndex;
        }
    }

    static int addKey(List<String> keys, String key, boolean caseInsensitive) {
        int keyIndex = indexOfKey(keys, key, caseInsensitive);
        if (keyIndex != Keys.UNKNOWN_KEY) {
            return keyIndex;
        }
        keyIndex = keys.size();
        keys.add(key);
        return keyIndex;
    }

    private static int indexOfKey(List<String> keys, String key, boolean caseInsensitive) {
        String normalized = normalize(key, caseInsensitive);
        for (int i = 0; i < keys.size(); i++) {
            if (normalize(keys.get(i), caseInsensitive).equals(normalized)) {
                return i;
            }
        }
        return Keys.UNKNOWN_KEY;
    }

    private static String normalize(String key, boolean caseInsensitive) {
        return caseInsensitive ? key.toLowerCase(Locale.ROOT) : key;
    }

    private boolean isIdentity(int[] propertyIndexes) {
        if (propertyIndexes.length != properties.length) {
            return false;
        }
        for (int i = 0; i < propertyIndexes.length; i++) {
            if (propertyIndexes[i] != i) {
                return false;
            }
        }
        return true;
    }

    private int propertyIndexOfSlow(String name) {
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

    List<String> getKeys() {
        return keys;
    }

    Consumer newConsumer() {
        if (propertiesMask == 0) {
            return new ConsumerBig();
        }
        return keyIndexesIdentity ? new ConsumerSmallIdentity() : new ConsumerSmall();
    }

    /**
     * Properties consumer.
     */
    abstract sealed class Consumer {
        private Consumer() {
        }

        public DeserBean.@Nullable DerProperty<T, Object> consumeKeyIndex(int keyIndex) {
            int propertyIndex = propertyIndexForKeyIndex(keyIndex);
            if (propertyIndex == Keys.UNKNOWN_KEY) {
                return null;
            }
            if (isConsumed(propertyIndex)) {
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

    private non-sealed class ConsumerSmall extends Consumer {
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

    private final class ConsumerSmallIdentity extends ConsumerSmall {

        @Override
        public DeserBean.@Nullable DerProperty<T, Object> consumeKeyIndex(int keyIndex) {
            if (!keyRange.contains(keyIndex) || isConsumed(keyIndex)) {
                return null;
            }
            setConsumed(keyIndex);
            return properties[keyIndex];
        }
    }

    static class Builder<T> {

        private final BeanIntrospection<T> beanIntrospection;
        private final int[] originalNameToPropertiesMapping;
        @Nullable
        private Map<String, Integer> nameToPropertiesMapping;
        private final boolean acceptCaseInsensitiveProperties;

        private final List<DeserBean.DerProperty<T, Object>> mutableProperties;

        Builder(BeanIntrospection<T> beanIntrospection) {
            this(beanIntrospection, beanIntrospection.getBeanProperties().size());
        }

        Builder(BeanIntrospection<T> beanIntrospection, int expectedPropertiesSize) {
            this(beanIntrospection, expectedPropertiesSize, false);
        }

        Builder(BeanIntrospection<T> beanIntrospection, int expectedPropertiesSize, boolean acceptCaseInsensitiveProperties) {
            this.beanIntrospection = beanIntrospection;
            int beanPropertiesSize = beanIntrospection.getBeanProperties().size();
            this.originalNameToPropertiesMapping = new int[beanPropertiesSize];
            Arrays.fill(originalNameToPropertiesMapping, -1);
            this.mutableProperties = new ArrayList<>(expectedPropertiesSize);
            this.acceptCaseInsensitiveProperties = acceptCaseInsensitiveProperties;
        }

        void register(String name, DeserBean.DerProperty<T, Object> derProperty, boolean addAliases) {
            int newPropertyIndex = mutableProperties.size();
            if (derProperty.beanProperty != null && derProperty.beanProperty.getDeclaringBean() == beanIntrospection && name.equals(derProperty.beanProperty.getName())) {
                int propertyIndex = beanIntrospection.propertyIndexOf(name);
                if (propertyIndex >= 0) {
                    originalNameToPropertiesMapping[propertyIndex] = newPropertyIndex;
                } else {
                    addMappedProperty(name, newPropertyIndex);
                }
            } else {
                addMappedProperty(name, newPropertyIndex);
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

        private void addMappedProperty(String name, int propertyIndex) {
            if (nameToPropertiesMapping == null) {
                nameToPropertiesMapping = new HashMap<>();
            }
            nameToPropertiesMapping.put(name, propertyIndex);
        }

        PropertiesBag<T> buildNotNull(List<String> keys) {
            return new PropertiesBag<>(
                beanIntrospection,
                originalNameToPropertiesMapping,
                mutableProperties.toArray(DeserBean.DerProperty[]::new),
                nameToPropertiesMapping,
                acceptCaseInsensitiveProperties,
                keys
            );
        }

        @Nullable
        PropertiesBag<T> build(List<String> keys) {
            if (mutableProperties.isEmpty()) {
                return null;
            }
            return buildNotNull(keys);
        }

    }

    private record Range(int start, int end) {

        private boolean contains(int index) {
            return index >= start && index < end;
        }

        private int offset(int index) {
            return index - start;
        }
    }

    private static final class KeyIndex {
        private final List<String> keys;
        private final Range range;
        private final int[] propertyIndexes;
        private final int @Nullable [] remappedPropertyIndexes;
        private final boolean identity;

        private KeyIndex(List<String> keys,
                         Range range,
                         int[] propertyIndexes,
                         int @Nullable [] remappedPropertyIndexes,
                         boolean identity) {
            this.keys = keys;
            this.range = range;
            this.propertyIndexes = propertyIndexes;
            this.remappedPropertyIndexes = remappedPropertyIndexes;
            this.identity = identity;
        }
    }

}
