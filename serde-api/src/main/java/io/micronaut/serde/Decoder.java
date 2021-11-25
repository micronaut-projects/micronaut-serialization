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

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Core interface for decoding values from a serialized format such as JSON.
 *
 * @since 1.0.0
 */
public interface Decoder {
    /**
     * Start decoding an array.
     * @param type The array type
     * @return The array decoder
     * @throws IOException If an unrecoverable error occurs
     */
    @NonNull
    Decoder decodeArray(Argument<?> type) throws IOException;

    /**
     * Start decoding an array.
     * @return The array decoder
     * @throws IOException If an unrecoverable error occurs
     */
    @NonNull
    default Decoder decodeArray() throws IOException {
        return decodeArray(Argument.OBJECT_ARGUMENT);
    }

    /**
     * @return Returns {@code true} if another array value is available.
     * @throws IOException If an unrecoverable error occurs
     */
    boolean hasNextArrayValue() throws IOException;

    /**
     * Decodes an object.
     * @param type The type, never {@code null}
     * @return The object decoder
     * @throws IOException If an unrecoverable error occurs
     */
    @NonNull
    Decoder decodeObject(@NonNull Argument<?> type) throws IOException;

    /**
     * Decodes an object.
     * @return The object decoder
     * @throws IOException If an unrecoverable error occurs
     */
    @NonNull
    default Decoder decodeObject() throws IOException {
        return decodeObject(Argument.OBJECT_ARGUMENT);
    }

    /**
     * Decodes a key, if there are no more keys to decode returns {@code null}.
     * @return The key or {@code null} if there aren't any more keys
     * @throws IOException If an unrecoverable error occurs
     */
    @Nullable
    String decodeKey() throws IOException;

    /**
     * Decodes a string.
     * @return The string, never {@code null}
     * @throws IOException If an unrecoverable error occurs
     */
    @NonNull
    String decodeString() throws IOException;

    /**
     * Decodes a boolean.
     * @return The boolean
     * @throws IOException If an unrecoverable error occurs
     */
    boolean decodeBoolean() throws IOException;

    /**
     * Decodes a byte.
     * @return The byte
     * @throws IOException If an unrecoverable error occurs
     */
    byte decodeByte() throws IOException;

    /**
     * Decodes a short.
     * @return The short
     * @throws IOException If an unrecoverable error occurs
     */
    short decodeShort() throws IOException;

    /**
     * Decodes a char.
     * @return The char
     * @throws IOException If an unrecoverable error occurs
     */
    char decodeChar() throws IOException;

    /**
     * Decodes a int.
     * @return The int
     * @throws IOException If an unrecoverable error occurs
     */
    int decodeInt() throws IOException;

    /**
     * Decodes a long.
     * @return The long
     * @throws IOException If an unrecoverable error occurs
     */
    long decodeLong() throws IOException;

    /**
     * Decodes a float.
     * @return The float
     * @throws IOException If an unrecoverable error occurs
     */
    float decodeFloat() throws IOException;

    /**
     * Decodes a double.
     * @return The double
     * @throws IOException If an unrecoverable error occurs
     */
    double decodeDouble() throws IOException;

    /**
     * Decodes a BigInteger.
     * @return The BigInteger, never {@code null}
     * @throws IOException If an unrecoverable error occurs
     */
    @NonNull
    BigInteger decodeBigInteger() throws IOException;

    /**
     * Decodes a BigDecimal.
     * @return The BigDecimal, never {@code null}
     * @throws IOException If an unrecoverable error occurs
     */
    @NonNull
    BigDecimal decodeBigDecimal() throws IOException;

    /**
     * Attempt to decode a null value. Returns {@code false} if this value is not null, and another method should be
     * used for decoding. Returns {@code true} if this value was null, and the cursor has been advanced to the next
     * value.
     * @throws IOException If an unrecoverable error occurs
     * @return Returns {@code true} if the value was {@code null}
     */
    boolean decodeNull() throws IOException;

    /**
     * Decodes the current state into an arbitrary object.
     *
     * <p>The following should be decoded by this method:</p>
     *
     * <ul>
     *  <li>Object types will be decoded into a {@link java.util.Map}</li>
     *  <li>Array types will be decoded into a {@link java.util.List}</li>
     *  <li>JSON primitive types into the equivalent Java wrapper type</li>
     * </ul>
     *
     *
     * @return The decoded object
     * @throws IOException If an unrecoverable error occurs
     */
    @Nullable
    Object decodeArbitrary() throws IOException;

    /**
     * Buffer the whole subtree of this value and return it as a new {@link Decoder}. The returned {@link Decoder} can
     * be used independently to this {@link Decoder}. This means actual parsing of the subtree can be delayed.
     * <p>
     * The returned {@link Decoder} should behave identically to this {@link Decoder}. This means that for example
     * {@code decoder.decodeDouble()} should be equivalent to {@code decoder.decodeBuffer().decodeDouble()}.
     *
     * @return An independent decoder that visits this subtree.
     * @throws IOException If an unrecoverable error occurs
     */
    Decoder decodeBuffer() throws IOException;

    /**
     * Skips the current value.
     * @throws IOException If an unrecoverable error occurs
     */
    void skipValue() throws IOException;

    /**
     * @throws IllegalStateException If there are still elements left to consume
     * @throws IOException If an unrecoverable error occurs
     */
    void finishStructure() throws IOException;

    /**
     * Creates an exception for the given message.
     * @param message The message, never {@code null}
     * @return The exception, never {@code null}
     */
    @NonNull IOException createDeserializationException(@NonNull String message);
}
