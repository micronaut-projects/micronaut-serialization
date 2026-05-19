/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.serde;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.util.StringIntMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Support logic for creating {@link Keys} instances.
 *
 * @author Denis Stepanov
 * @since 3.0
 */
@Internal
public final class KeysSupport {

    private static final Object[][] EMPTY_CONTRIBUTIONS = new Object[0][];

    private KeysSupport() {
    }

    /**
     * Create a key set for the supplied keys.
     *
     * @param keys The keys
     * @return The key set
     */
    public static Keys create(List<String> keys) {
        return create(keys, false);
    }

    /**
     * Create a key set for the supplied keys.
     *
     * @param keys The keys
     * @param caseInsensitive Whether key matching should be case-insensitive
     * @return The key set
     */
    public static Keys create(List<String> keys, boolean caseInsensitive) {
        List<String> keyList = List.copyOf(Objects.requireNonNull(keys, "keys"));
        return new DefaultKeys(keyList, caseInsensitive, createContributedKeys(keyList, caseInsensitive));
    }

    /**
     * Find the contributed key data index for the given provider.
     *
     * @param provider The keys provider
     * @return The contributed data index, or {@code -1} if no provider contributes this type
     */
    public static int indexOf(KeysProvider provider) {
        Objects.requireNonNull(provider, "provider");
        Class<?> keysType = Objects.requireNonNull(provider.keysType(), "keysType");
        List<KeysProvider> providers = LazyKeysProviders.PROVIDERS;
        for (int i = 0; i < providers.size(); i++) {
            if (providers.get(i).keysType().equals(keysType)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Find contributed key data by index.
     *
     * @param keys The keys
     * @param keysIndex The keys data index
     * @return The contributed key data
     */
    public static Object[] get(Keys keys, int keysIndex) {
        return ((DefaultKeys) keys).get(keysIndex);
    }

    static String keyAt(Keys keys, int keyIndex) {
        return ((DefaultKeys) keys).keyAt(keyIndex);
    }

    private static Object[][] createContributedKeys(List<String> keys, boolean caseInsensitive) {
        List<KeysProvider> providers = LazyKeysProviders.PROVIDERS;
        if (providers.isEmpty()) {
            return EMPTY_CONTRIBUTIONS;
        }
        Object[][] contributions = new Object[providers.size()][];
        for (int i = 0; i < providers.size(); i++) {
            contributions[i] = Objects.requireNonNull(providers.get(i).create(keys, caseInsensitive), "keys contribution");
        }
        return contributions;
    }

    private static final class DefaultKeys implements Keys {
        private final List<String> keys;
        private final boolean caseInsensitive;
        private final StringIntMap keyToIndex;
        private final Object[][] contributedKeys;

        private DefaultKeys(List<String> keys, boolean caseInsensitive, Object[][] contributedKeys) {
            this.keys = keys;
            this.caseInsensitive = caseInsensitive;
            this.keyToIndex = new StringIntMap(keys.size());
            for (int i = 0; i < keys.size(); i++) {
                String key = normalize(keys.get(i));
                if (keyToIndex.get(key, Keys.UNKNOWN_KEY) == Keys.UNKNOWN_KEY) {
                    keyToIndex.put(key, i);
                }
            }
            this.contributedKeys = contributedKeys;
        }

        @Override
        public boolean caseInsensitive() {
            return caseInsensitive;
        }

        @Override
        public int indexOf(String key) {
            Objects.requireNonNull(key, "key");
            return keyToIndex.get(normalize(key), Keys.UNKNOWN_KEY);
        }

        private Object[] get(int keysIndex) {
            return contributedKeys[keysIndex];
        }

        private String keyAt(int keyIndex) {
            return keys.get(keyIndex);
        }

        private String normalize(String key) {
            return caseInsensitive ? key.toLowerCase(Locale.ROOT) : key;
        }
    }

    private static final class LazyKeysProviders {
        private static final List<KeysProvider> PROVIDERS;

        static {
            List<KeysProvider> providers = new ArrayList<>(2);
            SoftServiceLoader.load(KeysProvider.class, KeysSupport.class.getClassLoader())
                .disableFork()
                .collectAll(providers);
            PROVIDERS = Collections.unmodifiableList(providers);
        }
    }
}
