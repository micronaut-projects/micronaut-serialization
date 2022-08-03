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
package io.micronaut.serde.oracle.jdbc.json;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import oracle.sql.json.OracleJsonGenerator;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Implementation of the {@link Encoder} interface for Oracle JDBC JSON.
 *
 * @author Denis Stepanov
 * @since 1.2.0
 */
@Internal
final class OracleJdbcJsonGeneratorEncoder implements Encoder {
    private final OracleJsonGenerator jsonGenerator;
    private final OracleJdbcJsonGeneratorEncoder parent;
    private String currentKey;
    private int currentIndex;

    OracleJdbcJsonGeneratorEncoder(OracleJsonGenerator jsonGenerator) {
        this.jsonGenerator = jsonGenerator;
        this.parent = null;
    }

    OracleJdbcJsonGeneratorEncoder(OracleJdbcJsonGeneratorEncoder parent) {
        this.jsonGenerator = parent.jsonGenerator;
        this.parent = parent;
    }

    private void postEncodeValue() {
        currentIndex++;
    }

    @Override
    public Encoder encodeArray(Argument<?> type) {
        jsonGenerator.writeStartArray();
        return new OracleJdbcJsonGeneratorEncoder(this);
    }

    @Override
    public Encoder encodeObject(Argument<?> type) {
        jsonGenerator.writeStartObject();
        return new OracleJdbcJsonGeneratorEncoder(this);
    }

    @Override
    public void finishStructure() {
        if (parent == null) {
            throw new IllegalStateException("Not a structure");
        }
        jsonGenerator.writeEnd();
        parent.postEncodeValue();
    }

    @Override
    public void encodeKey(String key) {
        jsonGenerator.writeKey(key);
        this.currentKey = key;
    }

    @Override
    public void encodeString(String value) {
        jsonGenerator.write(value);
        postEncodeValue();
    }

    @Override
    public void encodeBoolean(boolean value) {
        jsonGenerator.write(value);
        postEncodeValue();
    }

    @Override
    public void encodeByte(byte value) {
        jsonGenerator.write(value);
        postEncodeValue();
    }

    @Override
    public void encodeShort(short value) {
        jsonGenerator.write(value);
        postEncodeValue();
    }

    @Override
    public void encodeChar(char value) {
        jsonGenerator.write(value);
        postEncodeValue();
    }

    @Override
    public void encodeInt(int value) {
        jsonGenerator.write(value);
        postEncodeValue();
    }

    @Override
    public void encodeLong(long value) {
        jsonGenerator.write(value);
        postEncodeValue();
    }

    @Override
    public void encodeFloat(float value) {
        jsonGenerator.write(value);
        postEncodeValue();
    }

    @Override
    public void encodeDouble(double value) {
        jsonGenerator.write(value);
        postEncodeValue();
    }

    @Override
    public void encodeBigInteger(BigInteger value) {
        jsonGenerator.write(value);
        postEncodeValue();
    }

    @Override
    public void encodeBigDecimal(BigDecimal value) {
        jsonGenerator.write(value);
        postEncodeValue();
    }

    @Override
    public void encodeNull() {
        jsonGenerator.writeNull();
        postEncodeValue();
    }

    @NonNull
    @Override
    public String currentPath() {
        StringBuilder builder = new StringBuilder();
        OracleJdbcJsonGeneratorEncoder enc = this;
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
