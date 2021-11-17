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

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Core interface for encoding a serialization format such as JSON.
 *
 * @since 1.0.0
 */
public interface Encoder {
    Encoder encodeArray() throws IOException;

    Encoder encodeObject() throws IOException;

    void finishStructure() throws IOException;

    void encodeKey(@NonNull String key) throws IOException;

    void encodeString(@NonNull String value) throws IOException;

    void encodeBoolean(boolean value) throws IOException;

    void encodeByte(byte value) throws IOException;

    void encodeShort(short value) throws IOException;

    void encodeChar(char value) throws IOException;

    void encodeInt(int value) throws IOException;

    void encodeLong(long value) throws IOException;

    void encodeFloat(float value) throws IOException;

    void encodeDouble(double value) throws IOException;

    void encodeBigInteger(@NonNull BigInteger value) throws IOException;

    void encodeBigDecimal(@NonNull BigDecimal value) throws IOException;

    void encodeNull() throws IOException;
}
