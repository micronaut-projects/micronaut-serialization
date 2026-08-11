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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Describes an object key and immutable configuration that key providers can precompute for their backend.
 *
 * @param name The serialized key name
 * @param metadata Backend-neutral serde configuration keyed by its internal member name
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public record KeyDescriptor(String name, Map<String, String> metadata) {

    /**
     * Creates an immutable key descriptor.
     *
     * @since 3.2
     */
    public KeyDescriptor {
        Objects.requireNonNull(name, "name");
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
    }

    /**
     * Creates a key descriptor without additional metadata.
     *
     * @param name The serialized key name
     * @since 3.2
     */
    public KeyDescriptor(String name) {
        this(name, Map.of());
    }

    /**
     * Creates a descriptor from alternating metadata names and values.
     *
     * @param name The serialized key name
     * @param metadata Alternating metadata names and values
     * @return The key descriptor
     * @since 3.2
     */
    public static KeyDescriptor create(String name, String... metadata) {
        if ((metadata.length & 1) != 0) {
            throw new IllegalArgumentException("Key metadata must contain alternating names and values");
        }
        Map<String, String> values = new HashMap<>(metadata.length / 2);
        for (int i = 0; i < metadata.length; i += 2) {
            values.put(metadata[i], metadata[i + 1]);
        }
        return new KeyDescriptor(name, values);
    }
}
