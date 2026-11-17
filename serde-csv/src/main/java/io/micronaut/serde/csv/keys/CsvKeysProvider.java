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
package io.micronaut.serde.csv.keys;

import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.Keys;
import io.micronaut.serde.KeysProvider;
import io.micronaut.serde.KeyDescriptor;
import io.micronaut.serde.KeysSupport;

import java.util.ArrayList;
import java.util.List;

/**
 * Contributes CSV key data to {@link io.micronaut.serde.Keys}.
 * <p>
 * The CSV keys provider returns the column header names as contributed key data.
 * When registered via {@code META-INF/services/io.micronaut.serde.KeysProvider},
 * its contributor index can be used by {@link io.micronaut.serde.csv.CsvEncoder}
 * instead of the hardcoded contributor index 0.
 *
 * @author Denis Stepanov
 * @since 3.3
 */
@Internal
public final class CsvKeysProvider implements KeysProvider {

    /** The contributor index of this KeysProvider in the service loader order. */
    public static final int CSV_KEYS_INDEX = computeIndex();

    private static int computeIndex() {
        try {
            return KeysSupport.indexOf(new CsvKeysProvider());
        } catch (Exception e) {
            // Service loader not yet initialized or provider not registered;
            // fall back to 0 (current behavior).
            return 0;
        }
    }

    @Override
    public Class<?> keysType() {
        return Keys.class;
    }

    @Override
    public Object[] create(List<String> keys) {
        return create(keys, false);
    }

    @Override
    public Object[] create(List<String> keys, boolean caseInsensitive) {
        // Return the column header names as contributed key data.
        // The contributions array at CSV_KEYS_INDEX will contain these names,
        // and [index] will retrieve the key at the given position.
        return keys.toArray();
    }

    @Override
    public Object[] createWithMetadata(List<KeyDescriptor> keys, boolean caseInsensitive) {
        List<String> keyNames = new ArrayList<>(keys.size());
        for (KeyDescriptor key : keys) {
            keyNames.add(key.name());
        }
        return keyNames.toArray();
    }
}
