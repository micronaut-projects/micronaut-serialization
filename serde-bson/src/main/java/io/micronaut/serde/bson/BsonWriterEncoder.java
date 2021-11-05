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
package io.micronaut.serde.bson;

import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.Encoder;
import org.bson.BsonWriter;
import org.bson.types.Decimal128;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Bson implementation of {@link Encoder}.
 *
 * @author Denis Stepanov
 */
@Internal
public final class BsonWriterEncoder implements Encoder {
    private final BsonWriter bsonWriter;
    private final boolean isArray;

    public BsonWriterEncoder(BsonWriter bsonWriter, boolean isArray) {
        this.bsonWriter = bsonWriter;
        this.isArray = isArray;
    }

    @Override
    public Encoder encodeArray() {
        bsonWriter.writeStartArray();
        if (isArray) {
            return this;
        }
        return new BsonWriterEncoder(bsonWriter, true);
    }

    @Override
    public Encoder encodeObject() {
        bsonWriter.writeStartDocument();
        if (!isArray) {
            return this;
        }
        return new BsonWriterEncoder(bsonWriter, false);
    }

    @Override
    public void finishStructure() {
        if (isArray) {
            bsonWriter.writeEndArray();
        } else {
            bsonWriter.writeEndDocument();
        }
    }

    @Override
    public void encodeKey(String key) {
        bsonWriter.writeName(key);
    }

    @Override
    public void encodeString(String value) {
        bsonWriter.writeString(value);
    }

    @Override
    public void encodeBoolean(boolean value) {
        bsonWriter.writeBoolean(value);
    }

    @Override
    public void encodeByte(byte value) {
        bsonWriter.writeInt32(value);
    }

    @Override
    public void encodeShort(short value) {
        bsonWriter.writeInt32(value);
    }

    @Override
    public void encodeChar(char value) {
        bsonWriter.writeInt32(value);
    }

    @Override
    public void encodeInt(int value) {
        bsonWriter.writeInt32(value);
    }

    @Override
    public void encodeLong(long value) {
        bsonWriter.writeInt64(value);
    }

    @Override
    public void encodeFloat(float value) {
        bsonWriter.writeDouble(value);
    }

    @Override
    public void encodeDouble(double value) {
        bsonWriter.writeDouble(value);
    }

    @Override
    public void encodeBigInteger(BigInteger value) {
        encodeBigDecimal(new BigDecimal(value));
    }

    @Override
    public void encodeBigDecimal(BigDecimal value) {
        bsonWriter.writeDecimal128(new Decimal128(value));
    }

    @Override
    public void encodeNull() {
        bsonWriter.writeNull();
    }

    public void encodeDecimal128(Decimal128 value) {
        bsonWriter.writeDecimal128(value);
    }

    @Override
    public boolean hasView(Class<?>... views) {
        // TODO
        return false;
    }
}
