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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import org.bson.BsonWriter;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;

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
    private final BsonWriterEncoder parent;

    private String currentKey = null;
    private int currentIndex = 0;

    public BsonWriterEncoder(BsonWriter bsonWriter) {
        this.bsonWriter = bsonWriter;
        this.isArray = false;
        this.parent = null;
    }

    private void postEncodeValue() {
        currentIndex++;
    }

    private BsonWriterEncoder(BsonWriterEncoder parent, boolean isArray) {
        this.bsonWriter = parent.bsonWriter;
        this.isArray = isArray;
        this.parent = parent;
    }

    @Override
    public Encoder encodeArray(Argument<?> type) {
        bsonWriter.writeStartArray();
        return new BsonWriterEncoder(this, true);
    }

    @Override
    public Encoder encodeObject(Argument<?> type) {
        bsonWriter.writeStartDocument();
        return new BsonWriterEncoder(this, false);
    }

    @Override
    public void finishStructure() {
        if (parent == null) {
            throw new IllegalStateException("Not in a structure");
        }
        if (isArray) {
            bsonWriter.writeEndArray();
        } else {
            bsonWriter.writeEndDocument();
        }
        parent.postEncodeValue();
    }

    @Override
    public void encodeKey(String key) {
        this.currentKey = key;
        bsonWriter.writeName(key);
    }

    @Override
    public void encodeString(String value) {
        bsonWriter.writeString(value);
        postEncodeValue();
    }

    @Override
    public void encodeBoolean(boolean value) {
        bsonWriter.writeBoolean(value);
        postEncodeValue();
    }

    @Override
    public void encodeByte(byte value) {
        bsonWriter.writeInt32(value);
        postEncodeValue();
    }

    @Override
    public void encodeShort(short value) {
        bsonWriter.writeInt32(value);
        postEncodeValue();
    }

    @Override
    public void encodeChar(char value) {
        bsonWriter.writeInt32(value);
        postEncodeValue();
    }

    @Override
    public void encodeInt(int value) {
        bsonWriter.writeInt32(value);
        postEncodeValue();
    }

    @Override
    public void encodeLong(long value) {
        bsonWriter.writeInt64(value);
        postEncodeValue();
    }

    @Override
    public void encodeFloat(float value) {
        bsonWriter.writeDouble(value);
        postEncodeValue();
    }

    @Override
    public void encodeDouble(double value) {
        bsonWriter.writeDouble(value);
        postEncodeValue();
    }

    @Override
    public void encodeBigInteger(BigInteger value) {
        encodeBigDecimal(new BigDecimal(value));
        postEncodeValue();
    }

    @Override
    public void encodeBigDecimal(BigDecimal value) {
        bsonWriter.writeDecimal128(new Decimal128(value));
        postEncodeValue();
    }

    @Override
    public void encodeNull() {
        bsonWriter.writeNull();
        postEncodeValue();
    }

    @NonNull
    @Override
    public String currentPath() {
        StringBuilder builder = new StringBuilder();
        BsonWriterEncoder enc = this;
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

    public void encodeDecimal128(Decimal128 value) {
        bsonWriter.writeDecimal128(value);
        postEncodeValue();
    }

    public void encodeObjectId(ObjectId value) {
        bsonWriter.writeObjectId(value);
        postEncodeValue();
    }

    public BsonWriter getBsonWriter() {
        return bsonWriter;
    }
}
