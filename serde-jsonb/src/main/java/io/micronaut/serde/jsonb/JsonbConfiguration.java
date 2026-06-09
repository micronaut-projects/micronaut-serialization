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
package io.micronaut.serde.jsonb;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

/**
 * Configuration for the Micronaut JSON-B integration.
 *
 * @since 3.1.0
 */
@ConfigurationProperties(JsonbConfiguration.PREFIX)
public interface JsonbConfiguration {
    /**
     * Configuration prefix for Micronaut JSON-B integration.
     *
     * @since 3.1.0
     */
    String PREFIX = "micronaut.serde.jsonb";

    /**
     * JSON-B configuration property for additional reduced-context package prefixes.
     *
     * @since 3.1.0
     */
    String ADDITIONAL_PACKAGES = PREFIX + ".additional-packages";

    /**
     * Configuration property that controls reflection fallback for the context-created JSON-B bean.
     *
     * @since 3.1.0
     */
    String REFLECTION = PREFIX + ".reflection";

    /**
     * Reflection fallback mode for the context-created JSON-B bean.
     *
     * @return The reflection fallback mode
     * @since 3.1.0
     */
    @Bindable(defaultValue = "OFF")
    Reflection getReflection();

    /**
     * JSON-B reflection fallback mode.
     *
     * @since 3.1.0
     */
    enum Reflection {
        /**
         * Always use reflection fallback behavior.
         */
        ON,

        /**
         * Never use reflection fallback behavior.
         */
        OFF,

        /**
         * Use reflection fallback only when the JSON-B configuration requires it.
         */
        AUTO
    }
}
