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
package io.micronaut.json;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

public interface Decoder {
    @NonNull
    Decoder decodeArray() throws IOException;

    boolean hasNextArrayValue() throws IOException;

    @NonNull
    Decoder decodeObject() throws IOException;

    @Nullable
    String decodeKey() throws IOException;

    @NonNull
    String decodeString() throws IOException;

    boolean decodeBoolean() throws IOException;

    byte decodeByte() throws IOException;

    short decodeShort() throws IOException;

    char decodeChar() throws IOException;

    int decodeInt() throws IOException;

    long decodeLong() throws IOException;

    float decodeFloat() throws IOException;

    double decodeDouble() throws IOException;

    @NonNull
    BigInteger decodeBigInteger() throws IOException;

    @NonNull
    BigDecimal decodeBigDecimal() throws IOException;

    /**
     * Attempt to decode a null value. Returns {@code false} if this value is not null, and another method should be
     * used for decoding. Returns {@code true} if this value was null, and the cursor has been advanced to the next
     * value.
     */
    boolean decodeNull() throws IOException;

    @Nullable
    Object decodeArbitrary() throws IOException;

    void skipValue() throws IOException;

    void finishStructure() throws IOException;

    IOException createDeserializationException(String message);

    /**
     * @param views Views to check.
     * @return {@code true} iff any of the given views is enabled.
     */
    boolean hasView(Class<?>... views);
}
