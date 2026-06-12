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

import java.util.List;

/**
 * A reusable key set for known object keys.
 *
 * @author Denis Stepanov
 * @since 3.1
 */
@Internal
public interface Keys {

    /**
     * Returned by {@link #indexOf(String)} when a key is unknown.
     */
    int UNKNOWN_KEY = -1;

    /**
     * Create a key set for the supplied keys.
     *
     * @param keys The keys
     * @return The key set
     */
    static Keys create(List<String> keys) {
        return KeysSupport.create(keys);
    }

    /**
     * Create a key set for the supplied keys.
     *
     * @param keys The keys
     * @param caseInsensitive Whether key matching should be case-insensitive
     * @return The key set
     */
    static Keys create(List<String> keys, boolean caseInsensitive) {
        return KeysSupport.create(keys, caseInsensitive);
    }

    /**
     * Create a key set for the supplied keys.
     *
     * @param keys The keys
     * @return The key set
     */
    static Keys create(String... keys) {
        return KeysSupport.create(List.of(keys));
    }

    /**
     * Create a key set for the supplied keys.
     *
     * @param keys The keys
     * @param caseInsensitive Whether key matching should be case-insensitive
     * @return The key set
     */
    static Keys create(String[] keys, boolean caseInsensitive) {
        return KeysSupport.create(List.of(keys), caseInsensitive);
    }

    /**
     * Find the index of the supplied key.
     *
     * @param key The key
     * @return The key index, or {@link #UNKNOWN_KEY} if no key matches
     */
    int indexOf(String key);

    /**
     * Whether key matching should be case-insensitive.
     *
     * @return True if key matching should ignore case
     */
    default boolean caseInsensitive() {
        return false;
    }
}
