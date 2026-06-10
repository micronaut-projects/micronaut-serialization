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
package io.micronaut.serde.jsonb;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import jakarta.json.bind.config.BinaryDataStrategy;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Base64;

/**
 * JSON-B encoder wrapper around a Serde encoder.
 * <p>
 * The wrapper keeps normal Serde serialization in control while applying the
 * JSON-B-specific scalar rules that differ from the generic encoder contract,
 * such as binary data strategies and character encoding.
 *
 * @param delegate The underlying Serde encoder
 * @param binaryDataStrategy The configured JSON-B binary data strategy
 */
@Internal
record JsonbEncoder(Encoder delegate, String binaryDataStrategy) implements Encoder {

    @Override
    public Encoder encodeArray(Argument<?> type) throws IOException {
        return new JsonbEncoder(delegate.encodeArray(type), binaryDataStrategy);
    }

    @Override
    public Encoder encodeObject(Argument<?> type) throws IOException {
        return new JsonbEncoder(delegate.encodeObject(type), binaryDataStrategy);
    }

    @Override
    public void finishStructure() throws IOException {
        delegate.finishStructure();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public void encodeKey(String key) throws IOException {
        delegate.encodeKey(key);
    }

    @Override
    public void encodeString(String value) throws IOException {
        delegate.encodeString(value);
    }

    @Override
    public void encodeBoolean(boolean value) throws IOException {
        delegate.encodeBoolean(value);
    }

    @Override
    public void encodeByte(byte value) throws IOException {
        delegate.encodeByte(value);
    }

    @Override
    public void encodeShort(short value) throws IOException {
        delegate.encodeShort(value);
    }

    @Override
    public void encodeChar(char value) throws IOException {
        delegate.encodeString(String.valueOf(value));
    }

    @Override
    public void encodeInt(int value) throws IOException {
        delegate.encodeInt(value);
    }

    @Override
    public void encodeLong(long value) throws IOException {
        delegate.encodeLong(value);
    }

    @Override
    public void encodeFloat(float value) throws IOException {
        delegate.encodeBigDecimal(new BigDecimal(Float.toString(value)));
    }

    @Override
    public void encodeDouble(double value) throws IOException {
        delegate.encodeDouble(value);
    }

    @Override
    public void encodeBigInteger(BigInteger value) throws IOException {
        delegate.encodeBigInteger(value);
    }

    @Override
    public void encodeBigDecimal(BigDecimal value) throws IOException {
        delegate.encodeBigDecimal(value);
    }

    @Override
    public void encodeBinary(byte[] data) throws IOException {
        if (BinaryDataStrategy.BASE_64_URL.equals(binaryDataStrategy)) {
            delegate.encodeString(Base64.getUrlEncoder().encodeToString(data));
        } else {
            delegate.encodeString(Base64.getEncoder().encodeToString(data));
        }
    }

    @Override
    public void encodeNull() throws IOException {
        delegate.encodeNull();
    }

    @Override
    public String currentPath() {
        return delegate.currentPath();
    }
}
