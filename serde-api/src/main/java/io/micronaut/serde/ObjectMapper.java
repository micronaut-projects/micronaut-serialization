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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonFeatures;
import io.micronaut.json.JsonMapper;

/**
 * Sub-interface of {@link JsonMapper} with customizations.
 *
 * @author graemerocher
 */
public interface ObjectMapper extends JsonMapper {
    /**
     * Read a value from the given input stream for the given type.
     * @param inputStream The input stream
     * @param type The type
     * @param <T> The generic type
     * @return The value or {@code null} if it decodes to null
     * @throws IOException If an unrecoverable error occurs
     */
    default @Nullable <T> T readValue(@NonNull InputStream inputStream, @NonNull Class<T> type) throws IOException {
        Objects.requireNonNull(type, "Type cannot be null");
        return readValue(inputStream, Argument.of(type));
    }

    /**
     * Read a value from the byte array for the given type.
     * @param byteArray The byte array
     * @param type The type
     * @param <T> The generic type
     * @return The value or {@code null} if it decodes to null
     * @throws IOException If an unrecoverable error occurs
     */
    default @Nullable <T> T readValue(@NonNull byte[] byteArray, @NonNull Class<T> type) throws IOException {
        Objects.requireNonNull(type, "Type cannot be null");
        return readValue(byteArray, Argument.of(type));
    }

    /**
     * Read a value from the given string for the given type.
     * @param string The string
     * @param type The type
     * @param <T> The generic type
     * @return The value or {@code null} if it decodes to null
     * @throws IOException If an unrecoverable error occurs
     */
    default @Nullable <T> T readValue(@NonNull String string, @NonNull Class<T> type) throws IOException {
        Objects.requireNonNull(type, "Type cannot be null");
        return readValue(string, Argument.of(type));
    }

    /**
     * Write the given value as a string.
     * @param object The object
     * @param <T> The generic type
     * @return The string
     * @throws IOException If an unrecoverable error occurs
     */
    @SuppressWarnings("unchecked")
    default  @NonNull <T> String writeValueAsString(@NonNull T object) throws IOException {
        Objects.requireNonNull(object, "Object cannot be null");
        return writeValueAsString((Argument<T>) Argument.of(object.getClass()), object, StandardCharsets.UTF_8);
    }

    @Override
    default JsonMapper cloneWithFeatures(JsonFeatures features) {
        return this;
    }

    /**
     * Write the given value as a string.
     * @param type The type, never {@code null}
     * @param object The object
     * @param <T> The generic type
     * @return The string
     * @throws IOException If an unrecoverable error occurs
     */
    default  @NonNull <T> String writeValueAsString(@NonNull Argument<T> type, @Nullable T object) throws IOException {
        return writeValueAsString(type, object, StandardCharsets.UTF_8);
    }

    /**
     * Write the given value as a string.
     * @param type The type, never {@code null}
     * @param object The object
     * @param charset The charset, never {@code null}
     * @param <T> The generic type
     * @return The string
     * @throws IOException If an unrecoverable error occurs
     */
    default  @NonNull <T> String writeValueAsString(@NonNull Argument<T> type, @Nullable T object, Charset charset) throws IOException {
        Objects.requireNonNull(charset, "Charset cannot be null");
        byte[] bytes = writeValueAsBytes(type, object);
        return new String(bytes, charset);
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
