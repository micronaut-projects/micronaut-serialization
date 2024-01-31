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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonFeatures;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.SerializationConfiguration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Sub-interface of {@link JsonMapper} with customizations.
 *
 * @author graemerocher
 */
public interface ObjectMapper extends JsonMapper {

    /**
     * Read value directly to as {@link JsonNode}.
     * @param byteArray The byte array
     * @param type The type
     * @return new {@link JsonNode} representing the JSON
     * @throws IOException
     */
    default JsonNode readValueToTree(byte[] byteArray, @NonNull Argument<?> type) throws IOException {
        return readValueToTree(new ByteArrayInputStream(byteArray), type);
    }

    /**
     * Read value directly to as {@link JsonNode}.
     * @param string The string
     * @param type The type
     * @return new {@link JsonNode} representing the JSON
     * @throws IOException
     */
    default JsonNode readValueToTree(@NonNull String string, @NonNull Argument<?> type) throws IOException {
        return readValueToTree(string.getBytes(StandardCharsets.UTF_8), type);
    }

    /**
     * Read value directly to as {@link JsonNode}.
     * @param is The input stream
     * @param type The type
     * @return new {@link JsonNode} representing the JSON
     * @throws IOException
     */
    default JsonNode readValueToTree(@NonNull InputStream is, @NonNull Argument<?> type) throws IOException {
        Object o = readValue(is, type);
        return writeValueToTree((Argument<? super Object>) type, o);
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
    @NonNull
    default ObjectMapper cloneWithConfiguration(
        @Nullable SerdeConfiguration configuration,
        @Nullable SerializationConfiguration serializationConfiguration,
        @Nullable DeserializationConfiguration deserializationConfiguration
    ) {
        return this;
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
    static @NonNull ObjectMapper getDefault() {
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
    static @NonNull CloseableObjectMapper create(Map<String, Object> configuration, String... packageNames) {
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
