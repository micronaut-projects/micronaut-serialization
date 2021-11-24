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
package io.micronaut.serde.json.stream;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import jakarta.json.stream.JsonGenerator;

final class JsonStreamEncoder implements Encoder {
    private final JsonGenerator jsonGenerator;

    public JsonStreamEncoder(JsonGenerator jsonGenerator) {
        this.jsonGenerator = jsonGenerator;
    }

    @Override
    public Encoder encodeArray(Argument<?> type) throws IOException {
        jsonGenerator.writeStartArray();
        return this;
    }

    @Override
    public Encoder encodeObject(Argument<?> type) throws IOException {
        jsonGenerator.writeStartObject();
        return this;
    }

    @Override
    public void finishStructure() throws IOException {
        jsonGenerator.writeEnd();
    }

    @Override
    public void encodeKey(String key) throws IOException {
        jsonGenerator.writeKey(key);
    }

    @Override
    public void encodeString(String value) throws IOException {
        jsonGenerator.write(value);
    }

    @Override
    public void encodeBoolean(boolean value) throws IOException {
        jsonGenerator.write(value);
    }

    @Override
    public void encodeByte(byte value) throws IOException {
        jsonGenerator.write(value);
    }

    @Override
    public void encodeShort(short value) throws IOException {
        jsonGenerator.write(value);
    }

    @Override
    public void encodeChar(char value) throws IOException {
        jsonGenerator.write(value);
    }

    @Override
    public void encodeInt(int value) throws IOException {
        jsonGenerator.write(value);
    }

    @Override
    public void encodeLong(long value) throws IOException {
        jsonGenerator.write(value);
    }

    @Override
    public void encodeFloat(float value) throws IOException {
        jsonGenerator.write(value);
    }

    @Override
    public void encodeDouble(double value) throws IOException {
        jsonGenerator.write(value);
    }

    @Override
    public void encodeBigInteger(BigInteger value) throws IOException {
        jsonGenerator.write(value);
    }

    @Override
    public void encodeBigDecimal(BigDecimal value) throws IOException {
        jsonGenerator.write(value);
    }

    @Override
    public void encodeNull() throws IOException {
        jsonGenerator.writeNull();
    }
}
