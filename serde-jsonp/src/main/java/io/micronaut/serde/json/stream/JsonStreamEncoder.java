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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.LimitingStream;
import jakarta.json.stream.JsonGenerator;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

final class JsonStreamEncoder extends LimitingStream implements Encoder {
    private final JsonGenerator jsonGenerator;
    private final JsonStreamEncoder parent;
    private String currentKey;
    private int currentIndex;

    public JsonStreamEncoder(JsonGenerator jsonGenerator, RemainingLimits remainingLimits) {
        super(remainingLimits);
        this.jsonGenerator = jsonGenerator;
        this.parent = null;
    }

    private JsonStreamEncoder(JsonStreamEncoder parent, RemainingLimits remainingLimits) {
        super(remainingLimits);
        this.jsonGenerator = parent.jsonGenerator;
        this.parent = parent;
    }

    private void postEncodeValue() {
        currentIndex++;
    }

    @Override
    public Encoder encodeArray(Argument<?> type) throws IOException {
        jsonGenerator.writeStartArray();
        return new JsonStreamEncoder(this, childLimits());
    }

    @Override
    public Encoder encodeObject(Argument<?> type) throws IOException {
        jsonGenerator.writeStartObject();
        return new JsonStreamEncoder(this, childLimits());
    }

    @Override
    public void finishStructure() throws IOException {
        if (parent == null) {
            throw new IllegalStateException("Not a structure");
        }
        jsonGenerator.writeEnd();
        parent.postEncodeValue();
    }

    @Override
    public void encodeKey(String key) throws IOException {
        jsonGenerator.writeKey(key);
        this.currentKey = key;
    }

    @Override
    public void encodeString(String value) throws IOException {
        jsonGenerator.write(value);
        postEncodeValue();
    }

    @Override
    public void encodeBoolean(boolean value) throws IOException {
        jsonGenerator.write(value);
        postEncodeValue();
    }

    @Override
    public void encodeByte(byte value) throws IOException {
        jsonGenerator.write(value);
        postEncodeValue();
    }

    @Override
    public void encodeShort(short value) throws IOException {
        jsonGenerator.write(value);
        postEncodeValue();
    }

    @Override
    public void encodeChar(char value) throws IOException {
        jsonGenerator.write(value);
        postEncodeValue();
    }

    @Override
    public void encodeInt(int value) throws IOException {
        jsonGenerator.write(value);
        postEncodeValue();
    }

    @Override
    public void encodeLong(long value) throws IOException {
        jsonGenerator.write(value);
        postEncodeValue();
    }

    @Override
    public void encodeFloat(float value) throws IOException {
        jsonGenerator.write(value);
        postEncodeValue();
    }

    @Override
    public void encodeDouble(double value) throws IOException {
        jsonGenerator.write(value);
        postEncodeValue();
    }

    @Override
    public void encodeBigInteger(BigInteger value) throws IOException {
        jsonGenerator.write(value);
        postEncodeValue();
    }

    @Override
    public void encodeBigDecimal(BigDecimal value) throws IOException {
        jsonGenerator.write(value);
        postEncodeValue();
    }

    @Override
    public void encodeNull() throws IOException {
        jsonGenerator.writeNull();
        postEncodeValue();
    }

    @NonNull
    @Override
    public String currentPath() {
        StringBuilder builder = new StringBuilder();
        JsonStreamEncoder enc = this;
        while (enc != null) {
            if (enc != this) {
                builder.insert(0, "->");
            }
            if (enc.currentKey == null) {
                if (enc.parent != null) {
                    builder.insert(0, enc.currentIndex);
                }
            } else {
                builder.insert(0, enc.currentKey);
            }
            enc = enc.parent;
        }
        return builder.toString();
    }
}
