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
package io.micronaut.serde.support.util;

import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.Serde;
import org.jspecify.annotations.Nullable;

/**
 * Serde that can create a property-specific variant from resolved property metadata.
 *
 * @author Mousrij Hamza
 * @param <T> The serialized/deserialized type
 * @since 3.2
 */
@Internal
public interface PropertySpecificSerde<T> extends Serde<T> {

    /**
     * Returns a serde variant configured for the current property. by propagating the XML serde-config metadata to custom serde.
     * for maintainers consistency metadata should only be read in Serbean or Deserbean.
     *
     * @param configuration The resolved property configuration
     * @return The property-specific serde
     */
    Serde<T> forProperty(PropertyConfiguration configuration);

    /**
     * Resolved property-level serde configuration.
     *
     * @param name The resolved property name
     * @param xmlNamespace The XML namespace, if configured
     * @param xmlUseWrapping Whether XML iterable wrapping is enabled
     * @param xmlWrapperName The XML wrapper name, if configured
     * @param xmlAttribute Whether the property is serialized as an XML attribute
     */
    @Internal
    record PropertyConfiguration(
        String name,
        @Nullable String xmlNamespace,
        boolean xmlUseWrapping,
        @Nullable String xmlWrapperName,
        boolean xmlAttribute
    ) {
    }
}
