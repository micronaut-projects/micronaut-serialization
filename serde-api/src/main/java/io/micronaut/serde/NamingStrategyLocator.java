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
package io.micronaut.serde;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;
import io.micronaut.serde.exceptions.SerdeException;

/**
 * Locator interface for a naming strategy.
 *
 * @since 1.0.0
 */
public interface NamingStrategyLocator {

    /**
     * Gets a naming strategy.
     *
     * @param namingStrategyClass The naming strategy class, should not be {@code null}
     * @param <D>                 The naming strategy type
     * @return The naming strategy
     * @throws SerdeException if no naming strategy is found
     */
    @NonNull
    <D extends PropertyNamingStrategy> D findNamingStrategy(@NonNull Class<? extends D> namingStrategyClass)
            throws SerdeException;

}
