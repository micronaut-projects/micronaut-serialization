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
import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Support logic for creating {@link Keys} instances.
 *
 * @author Denis Stepanov
 * @since 3.1
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
        return new DefaultKeys(keyList, null, caseInsensitive, createContributedKeys(keyList, null, caseInsensitive));
    }

    /**
     * Create a key set with metadata available to backend-specific key providers.
     *
     * @param keys The key descriptors
     * @return The key set
     * @since 3.2
     */
    public static Keys createWithMetadata(List<KeyDescriptor> keys) {
        return createWithMetadata(keys, false);
    }

    /**
     * Create a key set with metadata available to backend-specific key providers.
     *
     * @param keys The key descriptors
     * @param caseInsensitive Whether key matching should be case-insensitive
     * @return The key set
     * @since 3.2
     */
    public static Keys createWithMetadata(List<KeyDescriptor> keys, boolean caseInsensitive) {
        List<KeyDescriptor> descriptors = List.copyOf(Objects.requireNonNull(keys, "keys"));
        List<String> keyList = descriptors.stream().map(KeyDescriptor::name).toList();
        return new DefaultKeys(
            keyList,
            descriptors,
            caseInsensitive,
            createContributedKeys(keyList, descriptors, caseInsensitive)
        );
    }

    /**
     * Find or register the contributed key data index for the given provider.
     *
     * @param provider The keys provider
     * @return The contributed data index
     */
    public static int indexOf(KeysProvider provider) {
        Objects.requireNonNull(provider, "provider");
        Class<?> keysType = Objects.requireNonNull(provider.keysType(), "keysType");
        return LazyKeysProviders.indexOf(keysType, provider);
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

    private static Object[][] createContributedKeys(List<String> keys,
                                                    @Nullable List<KeyDescriptor> descriptors,
                                                    boolean caseInsensitive) {
        List<KeysProvider> providers = LazyKeysProviders.SERVICE_PROVIDERS;
        if (providers.isEmpty()) {
            return EMPTY_CONTRIBUTIONS;
        }
        Object[][] contributions = new Object[providers.size()][];
        for (int i = 0; i < providers.size(); i++) {
            contributions[i] = createContribution(providers.get(i), keys, descriptors, caseInsensitive);
        }
        return contributions;
    }

    private static Object[] createContribution(KeysProvider provider,
                                               List<String> keys,
                                               @Nullable List<KeyDescriptor> descriptors,
                                               boolean caseInsensitive) {
        return Objects.requireNonNull(
            descriptors == null
                ? provider.create(keys, caseInsensitive)
                : provider.createWithMetadata(descriptors, caseInsensitive),
            "keys contribution"
        );
    }

    private static final class DefaultKeys implements Keys {
        private final List<String> keys;
        @Nullable
        private final List<KeyDescriptor> descriptors;
        private final boolean caseInsensitive;
        private final StringIntMap keyToIndex;
        private volatile Object[][] contributedKeys;

        private DefaultKeys(List<String> keys,
                            @Nullable List<KeyDescriptor> descriptors,
                            boolean caseInsensitive,
                            Object[][] contributedKeys) {
            this.keys = keys;
            this.descriptors = descriptors;
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
            Object[][] contributions = contributedKeys;
            if (keysIndex < contributions.length) {
                Object[] contribution = contributions[keysIndex];
                if (contribution != null) {
                    return contribution;
                }
            }
            return createLateContribution(keysIndex);
        }

        private synchronized Object[] createLateContribution(int keysIndex) {
            Object[][] contributions = contributedKeys;
            if (keysIndex < contributions.length && contributions[keysIndex] != null) {
                return contributions[keysIndex];
            }
            // Late providers are resolved lazily and only for the requested index, so providers whose
            // class loader has been discarded are never invoked again.
            Object[] contribution = createContribution(
                LazyKeysProviders.lateProvider(keysIndex),
                keys,
                descriptors,
                caseInsensitive
            );
            Object[][] expanded = Arrays.copyOf(contributions, Math.max(contributions.length, keysIndex + 1));
            expanded[keysIndex] = contribution;
            contributedKeys = expanded;
            return contribution;
        }

        private String keyAt(int keyIndex) {
            return keys.get(keyIndex);
        }

        private String normalize(String key) {
            return caseInsensitive ? key.toLowerCase(Locale.ROOT) : key;
        }
    }

    /**
     * A provider registered after service loading. Both references are weak so that registering a
     * provider from a child class loader does not keep that class loader reachable.
     *
     * @param keysType The contribution type
     * @param provider The provider
     */
    private record LateProvider(WeakReference<Class<?>> keysType, WeakReference<KeysProvider> provider) {
    }

    private static final class LazyKeysProviders {
        private static final List<KeysProvider> SERVICE_PROVIDERS;
        /**
         * Keeps late provider instances alive for as long as their own class is loaded. Guarded by {@link #LATE_PROVIDERS}.
         */
        private static final ClassValue<List<KeysProvider>> RETAINED_PROVIDERS = new ClassValue<>() {
            @Override
            protected List<KeysProvider> computeValue(Class<?> type) {
                return new ArrayList<>(1);
            }
        };
        private static final List<LateProvider> LATE_PROVIDERS = new ArrayList<>();

        static {
            List<KeysProvider> providers = new ArrayList<>(2);
            SoftServiceLoader.load(KeysProvider.class, KeysSupport.class.getClassLoader())
                .disableFork()
                .collectAll(providers);
            for (KeysProvider provider : providers) {
                Objects.requireNonNull(provider.keysType(), "keysType");
            }
            SERVICE_PROVIDERS = List.copyOf(providers);
        }

        private static int indexOf(Class<?> keysType, KeysProvider provider) {
            for (int i = 0; i < SERVICE_PROVIDERS.size(); i++) {
                if (SERVICE_PROVIDERS.get(i).keysType().equals(keysType)) {
                    return i;
                }
            }
            synchronized (LATE_PROVIDERS) {
                for (int i = 0; i < LATE_PROVIDERS.size(); i++) {
                    if (LATE_PROVIDERS.get(i).keysType().get() == keysType) {
                        return SERVICE_PROVIDERS.size() + i;
                    }
                }
                // Slots of unloaded providers are never reused: existing keys may still hold their contributions.
                RETAINED_PROVIDERS.get(provider.getClass()).add(provider);
                LATE_PROVIDERS.add(new LateProvider(new WeakReference<>(keysType), new WeakReference<>(provider)));
                return SERVICE_PROVIDERS.size() + LATE_PROVIDERS.size() - 1;
            }
        }

        private static KeysProvider lateProvider(int keysIndex) {
            int lateIndex = keysIndex - SERVICE_PROVIDERS.size();
            KeysProvider provider = null;
            synchronized (LATE_PROVIDERS) {
                if (lateIndex >= 0 && lateIndex < LATE_PROVIDERS.size()) {
                    provider = LATE_PROVIDERS.get(lateIndex).provider().get();
                }
            }
            if (provider == null) {
                throw new IllegalStateException("No keys provider registered for index: " + keysIndex);
            }
            return provider;
        }
    }
}
