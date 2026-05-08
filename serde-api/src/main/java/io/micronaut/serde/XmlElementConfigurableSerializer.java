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
 * Internal contract for serializers that support resolved XML element configuration.
 *
 * @param <T> The serialized type
 */
@Internal
public interface XmlElementConfigurableSerializer<T> {

    /**
     * Create a serializer variant configured with resolved XML element settings.
     *
     * @param localName The resolved local element name
     * @param namespace The namespace URI if present
     * @return The configured serializer
     */
    @NonNull Serializer<T> withXmlElement(@NonNull String localName, @Nullable String namespace);
}
