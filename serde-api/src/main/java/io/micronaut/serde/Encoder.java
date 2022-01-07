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
import io.micronaut.core.type.Argument;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Core interface for encoding a serialization format such as JSON.
 *
 * @since 1.0.0
 */
public interface Encoder extends AutoCloseable {
    /**
     * Encodes an array.
     * @param type The array type, never {@code null}
     * @return The encoder, never {@code null}
     * @throws IOException if an error occurs
     */
    @NonNull Encoder encodeArray(@NonNull Argument<?> type) throws IOException;

    /**
     * Encodes an object.
     * @param type The object type, never {@code null}
     * @return The encoder, never {@code null}
     * @throws IOException if an error occurs
     */
    @NonNull Encoder encodeObject(@NonNull Argument<?> type) throws IOException;

    /**
     * Finalize a previously created structure, like an array or object.
     * @throws IOException If an error occurs
     */
    void finishStructure() throws IOException;

    /**
     * Finalize the current structure. Equivalent to calling {@link #finishStructure()}.
     * @throws IOException If an unrecoverable error occurs
     */
    @Override
    default void close() throws IOException {
        finishStructure();
    }

    /**
     * Encode a key.
     * @param key The key, never {@code null}
     * @throws IOException If an error occurs
     */
    void encodeKey(@NonNull String key) throws IOException;

    /**
     * Encode a string.
     * @param value The string, never {@code null}
     * @throws IOException If an error occurs
     */
    void encodeString(@NonNull String value) throws IOException;

    /**
     * Encode a boolean.
     * @param value The boolean
     * @throws IOException If an error occurs
     */
    void encodeBoolean(boolean value) throws IOException;

    /**
     * Encode a byte.
     * @param value The byte
     * @throws IOException If an error occurs
     */
    void encodeByte(byte value) throws IOException;

    /**
     * Encode a short.
     * @param value The short
     * @throws IOException If an error occurs
     */
    void encodeShort(short value) throws IOException;

    /**
     * Encode a char.
     * @param value The char
     * @throws IOException If an error occurs
     */
    void encodeChar(char value) throws IOException;

    /**
     * Encode an int.
     * @param value The int
     * @throws IOException If an error occurs
     */
    void encodeInt(int value) throws IOException;

    /**
     * Encode a long.
     * @param value The long
     * @throws IOException If an error occurs
     */
    void encodeLong(long value) throws IOException;

    /**
     * Encode a float.
     * @param value The float
     * @throws IOException If an error occurs
     */
    void encodeFloat(float value) throws IOException;

    /**
     * Encode a double.
     * @param value The double
     * @throws IOException If an error occurs
     */
    void encodeDouble(double value) throws IOException;

    /**
     * Encode a BigInteger.
     * @param value The BigInteger, never {@code null}
     * @throws IOException If an error occurs
     */
    void encodeBigInteger(@NonNull BigInteger value) throws IOException;

    /**
     * Encode a BigDecimal.
     * @param value The BigDecimal, never {@code null}
     * @throws IOException If an error occurs
     */
    void encodeBigDecimal(@NonNull BigDecimal value) throws IOException;

    /**
     * Encode {@code null}.
     * @throws IOException If an error occurs
     */
    void encodeNull() throws IOException;

    /**
     * Return an analysis of the current path.
     *
     * @return The current path if known
     */
    default @NonNull String currentPath() {
        return "";
    }
}
