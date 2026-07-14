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
 * Service-loaded contributor for backend-specific key data.
 *
 * @author Denis Stepanov
 * @since 3.1
 */
@Internal
public interface KeysProvider {

    /**
     * The contribution type supplied by this provider.
     *
     * @return The contribution type
     */
    Class<?> keysType();

    /**
     * Create backend-specific data for the supplied keys.
     * The returned value must be safe to reuse across encoder and decoder instances.
     *
     * @param keys The keys
     * @return The backend-specific data
     */
    Object[] create(List<String> keys);

    /**
     * Create backend-specific data for the supplied keys.
     * The returned value must be safe to reuse across encoder and decoder instances.
     *
     * @param keys The keys
     * @param caseInsensitive Whether key matching should be case-insensitive
     * @return The backend-specific data
     */
    default Object[] create(List<String> keys, boolean caseInsensitive) {
        return create(keys);
    }
}
