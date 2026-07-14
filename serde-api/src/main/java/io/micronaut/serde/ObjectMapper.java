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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonFeatures;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.SerializationConfiguration;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;

/**
 * Sub-interface of {@link JsonMapper} with customizations.
 *
 * @author graemerocher
 */
public interface ObjectMapper extends JsonMapper {

    /**
     * Update an existing mutable value from the supplied override value.
     *
     * <p>The override value is converted to a JSON tree and applied through
     * {@link #updateValueFromTree(Object, JsonNode)}. Supported update semantics are implementation-specific;
     * immutable, creator-only, and builder-only values may be rejected.</p>
     *
     * @param valueToUpdate The existing value to update
     * @param overrides The override value containing fields to apply
     * @param <T> The value type
     * @return The updated {@code valueToUpdate}
     * @throws IOException If an I/O or decoding error occurs
     * @since 3.1
     */
    @Experimental
    @SuppressWarnings("unchecked")
    default <T> T updateValue(T valueToUpdate, @Nullable Object overrides) throws IOException {
        Objects.requireNonNull(valueToUpdate, "Value to update cannot be null");
        return updateValue(valueToUpdate, (Argument<T>) Argument.of(valueToUpdate.getClass()), overrides);
    }

    /**
     * Update an existing mutable value from the supplied override value.
     *
     * <p>The override value is converted to a JSON tree and applied through
     * {@link #updateValueFromTree(Object, JsonNode)}. Supported update semantics are implementation-specific;
     * immutable, creator-only, and builder-only values may be rejected.</p>
     *
     * @param valueToUpdate The existing value to update
     * @param type The type of the value to update
     * @param overrides The override value containing fields to apply
     * @param <T> The value type
     * @return The updated {@code valueToUpdate}
     * @throws IOException If an I/O or decoding error occurs
     * @since 3.1
     */
    @Experimental
    default <T> T updateValue(T valueToUpdate, Argument<T> type, @Nullable Object overrides) throws IOException {
        Objects.requireNonNull(valueToUpdate, "Value to update cannot be null");
        Objects.requireNonNull(type, "Type cannot be null");
        if (overrides == null) {
            return valueToUpdate;
        }
        JsonNode tree = overrides instanceof JsonNode jsonNode ? jsonNode : writeValueToTree(overrides);
        updateValueFromTree(valueToUpdate, tree);
        return valueToUpdate;
    }

    /**
     * Update an existing mutable value from JSON read from the supplied input stream.
     *
     * @param valueToUpdate The existing value to update
     * @param inputStream The input stream containing JSON fields to apply
     * @param <T> The value type
     * @return The updated {@code valueToUpdate}
     * @throws IOException If an I/O or decoding error occurs
     * @param type The type of the value to update
     * @since 3.1
     */
    @Experimental
    default <T> T updateValue(T valueToUpdate, Argument<T> type, InputStream inputStream) throws IOException {
        Objects.requireNonNull(valueToUpdate, "Value to update cannot be null");
        Objects.requireNonNull(type, "Type cannot be null");
        Objects.requireNonNull(inputStream, "Input stream cannot be null");
        JsonNode tree = readValue(inputStream, JsonNode.class);
        if (tree != null) {
            updateValueFromTree(valueToUpdate, tree);
        }
        return valueToUpdate;
    }

    /**
     * Update an existing mutable value from JSON read from the supplied byte array.
     *
     * @param valueToUpdate The existing value to update
     * @param byteArray The byte array containing JSON fields to apply
     * @param <T> The value type
     * @return The updated {@code valueToUpdate}
     * @throws IOException If an I/O or decoding error occurs
     * @param type The type of the value to update
     * @since 3.1
     */
    @Experimental
    default <T> T updateValue(T valueToUpdate, Argument<T> type, byte[] byteArray) throws IOException {
        Objects.requireNonNull(valueToUpdate, "Value to update cannot be null");
        Objects.requireNonNull(type, "Type cannot be null");
        Objects.requireNonNull(byteArray, "Byte array cannot be null");
        JsonNode tree = readValue(byteArray, JsonNode.class);
        if (tree != null) {
            updateValueFromTree(valueToUpdate, tree);
        }
        return valueToUpdate;
    }

    /**
     * Update an existing mutable value from JSON read from the supplied byte buffer.
     *
     * @param valueToUpdate The existing value to update
     * @param byteBuffer The byte buffer containing JSON fields to apply
     * @param <T> The value type
     * @return The updated {@code valueToUpdate}
     * @throws IOException If an I/O or decoding error occurs
     * @param type The type of the value to update
     * @since 3.1
     */
    @Experimental
    default <T> T updateValue(T valueToUpdate, Argument<T> type, ByteBuffer<?> byteBuffer) throws IOException {
        Objects.requireNonNull(valueToUpdate, "Value to update cannot be null");
        Objects.requireNonNull(type, "Type cannot be null");
        Objects.requireNonNull(byteBuffer, "Byte buffer cannot be null");
        return updateValue(valueToUpdate, type, byteBuffer.toByteArray());
    }

    @Override
    default JsonMapper cloneWithFeatures(JsonFeatures features) {
        return this;
    }

    /**
     * Optional feature. Create a new {@link ObjectMapper} with the given configuration values. A
     * {@code null} parameter indicates the old configuration should be used.
     *
     * @param configuration The {@link SerdeConfiguration}
     * @param serializationConfiguration The {@link SerializationConfiguration}
     * @param deserializationConfiguration The {@link DeserializationConfiguration}
     * @return A new {@link JsonMapper} with the updated config
     * @since 2.7.0
     */
    default ObjectMapper cloneWithConfiguration(
        @Nullable SerdeConfiguration configuration,
        @Nullable SerializationConfiguration serializationConfiguration,
        @Nullable DeserializationConfiguration deserializationConfiguration
    ) {
        return this;
    }

    /**
     * Optional feature. Create a new {@link ObjectMapper} with the given configuration values and introspections. A
     * {@code null} configuration parameter indicates the old configuration should be used.
     *
     * @param configuration The {@link SerdeConfiguration}
     * @param serializationConfiguration The {@link SerializationConfiguration}
     * @param deserializationConfiguration The {@link DeserializationConfiguration}
     * @param introspections The {@link SerdeIntrospections}
     * @return A new {@link JsonMapper} with the updated config and introspections
     * @since 3.1.0
     */
    default ObjectMapper cloneWithConfiguration(
        @Nullable SerdeConfiguration configuration,
        @Nullable SerializationConfiguration serializationConfiguration,
        @Nullable DeserializationConfiguration deserializationConfiguration,
        SerdeIntrospections introspections
    ) {
        return cloneWithConfiguration(configuration, serializationConfiguration, deserializationConfiguration);
    }

    /**
     * Returns the {@link SerdeRegistry} used by this object mapper, if possible.
     *
     * @return The serde registry
     */
    default SerdeRegistry getSerdeRegistry() {
        throw new UnsupportedOperationException("No accessible SerdeRegistry");
    }

    /**
     * Get the default ObjectMapper instance.
     *
     * <p>Note that this method returns
     * an ObjectMapper that does not include any custom defined serializers or deserializers
     * and in general should be avoided outside a few niche cases that require static access.</p>
     *
     * <p>Where possible you should use dependency injection to instead retrieve the ObjectMapper
     * from the application context.
     * </p>
     *
     * @return The default object mapper
     * @since 1.3.0
     */
    static ObjectMapper getDefault() {
        return ObjectMappers.resolveDefault();
    }

    /**
     * Creates a new custom {@link ObjectMapper} with additional beans (serializers, deserializers etc.) loaded
     * from the given package locations.
     *
     * @param configuration The configuration
     * @param packageNames The package names
     * @return The new object mapper
     * @since 1.5.1
     */
    static CloseableObjectMapper create(Map<String, Object> configuration, String... packageNames) {
        return ObjectMappers.create(configuration, packageNames);
    }

    /**
     * A closeable object mapper.
     *
     * @since 1.5.1
     */
    interface CloseableObjectMapper extends ObjectMapper, AutoCloseable {
        @Override
        void close();
    }
}
