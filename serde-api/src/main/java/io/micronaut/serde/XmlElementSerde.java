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
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Internal contract, where output depends on
 * the resolved {@link io.micronaut.serde.config.annotation.SerdeConfig} element metadata for the property currently being serialized.
 *
 * <p>The serializer resolution step creates a generic serde first and then calls
 * {@link #withXmlElement(String, String)} when XML metadata supplies a local name or
 * namespace.
 *
 * @param <T> The serialized type handled by the serde
 *
 * @see io.micronaut.serde.config.annotation.SerdeConfig
 * @see io.micronaut.serde.xml.serde
 * @since 3.0.0
 */
@Internal
public interface XmlElementSerde<T> {

    /**
     * Returns a serde variant configured with the resolved XML element settings
     * for the property currently being processed.
     *
     * @param localName The local XML element name to use for the property
     * @param namespace The namespace URI to use for the element, or {@code null}
     *                  when no namespace was configured
     * @return The configured serde; never {@code null}
     */
    @NonNull Serde<T> withXmlElement(@NonNull String localName, @Nullable String namespace);
}
