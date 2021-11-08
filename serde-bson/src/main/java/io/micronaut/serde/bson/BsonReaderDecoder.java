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

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.exceptions.SerdeException;
import org.bson.BsonReader;
import org.bson.BsonType;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;

/**
 * Bson implementation of {@link Decoder}.
 *
 * @author Denis Stepanov
 */
@Internal
public final class BsonReaderDecoder implements Decoder {
    private final BsonReader bsonReader;
    private final boolean inArray;
    private boolean arrayHasChildren;
    private final State state;

    public BsonReaderDecoder(BsonReader bsonReader) {
        this(bsonReader, false, new State());
    }

    private BsonReaderDecoder(BsonReader bsonReader, boolean inArray, State state) {
        this.bsonReader = bsonReader;
        this.inArray = inArray;
        this.state = state;
        next();
    }

    private void next() {
        state.currentBsonType = bsonReader.readBsonType();
    }

    @Override
    public Decoder decodeArray() throws IOException {
        if (state.currentBsonType == BsonType.ARRAY) {
            bsonReader.readStartArray();
        } else {
            throw createDeserializationException("Not an array");
        }
        return new BsonReaderDecoder(bsonReader, true, state);
    }

    @Override
    public boolean hasNextArrayValue() {
        if (!inArray) {
            return false;
        }
        if (isEndOfDocument()) {
            if (arrayHasChildren) {
                next();
                return !isEndOfDocument();
            } else {
                return false;
            }
        }
        return true;
    }

    private boolean isEndOfDocument() {
        return state.currentBsonType == BsonType.END_OF_DOCUMENT;
    }

    @Override
    public Decoder decodeObject() throws IOException {
        if (inArray) {
            arrayHasChildren = true;
        }
        if (state.currentBsonType == BsonType.DOCUMENT) {
            bsonReader.readStartDocument();
        } else {
            throw createDeserializationException("Not an object");
        }
        return new BsonReaderDecoder(bsonReader, false, state);
    }

    @Override
    public String decodeKey() {
        if (isEndOfDocument()) {
            return null;
        }
        return bsonReader.readName();
    }

    @Override
    public String decodeString() throws IOException {
        if (state.currentBsonType == BsonType.STRING) {
            try {
                return bsonReader.readString();
            } finally {
                next();
            }
        }
        throw createDeserializationException("Cannot decode String from: " + state.currentBsonType);
    }

    @Override
    public boolean decodeBoolean() throws IOException {
        if (state.currentBsonType == BsonType.BOOLEAN) {
            try {
                return bsonReader.readBoolean();
            } finally {
                next();
            }
        }
        throw createDeserializationException("Cannot decode Boolean from: " + state.currentBsonType);
    }

    @Override
    public byte decodeByte() throws IOException {
        return (byte) decodeInt();
    }

    @Override
    public short decodeShort() throws IOException {
        return (short) decodeInt();
    }

    @Override
    public char decodeChar() throws IOException {
        return (char) decodeInt();
    }

    @Override
    public int decodeInt() throws IOException {
        if (state.currentBsonType == BsonType.INT32) {
            try {
                return bsonReader.readInt32();
            } finally {
                next();
            }
        }
        throw createDeserializationException("Cannot decode int from: " + state.currentBsonType);
    }

    @Override
    public long decodeLong() throws IOException {
        switch (state.currentBsonType) {
            case INT32:
                try {
                    return bsonReader.readInt32();
                } finally {
                    next();
                }
            case INT64:
                try {
                    return bsonReader.readInt64();
                } finally {
                    next();
                }
            default:
                throw createDeserializationException("Cannot decode Long from: " + state.currentBsonType);
        }
    }

    @Override
    public float decodeFloat() throws IOException {
        if (state.currentBsonType == BsonType.DOUBLE) {
            try {
                return (float) bsonReader.readDouble();
            } finally {
                next();
            }
        }
        return decodeDecimal128().floatValue();
    }

    @Override
    public double decodeDouble() throws IOException {
        if (state.currentBsonType == BsonType.DOUBLE) {
            try {
                return bsonReader.readDouble();
            } finally {
                next();
            }
        }
        return decodeDecimal128().doubleValue();
    }

    @Override
    public BigInteger decodeBigInteger() throws IOException {
        switch (state.currentBsonType) {
            case INT32:
                return BigInteger.valueOf(decodeInt());
            case INT64:
                return BigInteger.valueOf(decodeLong());
            case DECIMAL128:
                return decodeDecimal128().bigDecimalValue().toBigInteger();
            default:
                throw createDeserializationException("Cannot decode BigInteger from: " + state.currentBsonType);
        }
    }

    @Override
    public BigDecimal decodeBigDecimal() throws IOException {
        switch (state.currentBsonType) {
            case INT32:
                return BigDecimal.valueOf(decodeInt());
            case INT64:
                return BigDecimal.valueOf(decodeLong());
            case DOUBLE:
                return BigDecimal.valueOf(decodeDouble());
            case DECIMAL128:
                return decodeDecimal128().bigDecimalValue();
            default:
                throw createDeserializationException("Cannot decode BigDecimal from: " + state.currentBsonType);
        }
    }

    @Override
    public boolean decodeNull() {
        if (state.currentBsonType == BsonType.NULL) {
            try {
                bsonReader.readNull();
                return true;
            } finally {
                next();
            }
        }
        return false;
    }

    @Override
    public Object decodeArbitrary() {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Decoder decodeBuffer() {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void skipValue() {
        try {
            bsonReader.skipValue();
        } finally {
            next();
        }
    }

    @Override
    public void finishStructure() throws IOException {
        if (isEndOfDocument()) {
            if (inArray) {
                bsonReader.readEndArray();
            } else {
                bsonReader.readEndDocument();
            }
        } else {
            throw createDeserializationException("Expected END_OF_DOCUMENT got: " + state.currentBsonType);
        }
    }

    @Override
    public IOException createDeserializationException(String message) {
        return new SerdeException(message + " \n at ");
    }

    @Override
    public boolean hasView(Class<?>... views) {
        return false;
    }

    /**
     * Decodes {@link Decimal128}.
     *
     * @return decoded value
     * @throws IOException
     */
    public Decimal128 decodeDecimal128() throws IOException {
        if (state.currentBsonType == BsonType.DECIMAL128) {
            try {
                return bsonReader.readDecimal128();
            } finally {
                next();
            }
        }
        throw createDeserializationException("Cannot decode Decimal128 from: " + state.currentBsonType);
    }

    /**
     * Decodes {@link ObjectId}.
     *
     * @return decoded value
     * @throws IOException
     */
    public ObjectId decodeObjectId() throws IOException {
        if (state.currentBsonType == BsonType.OBJECT_ID) {
            try {
                return bsonReader.readObjectId();
            } finally {
                next();
            }
        }
        throw createDeserializationException("Cannot decode ObjectId from: " + state.currentBsonType);
    }

    /**
     * Shared state to fix Bson's Json reader inconsistent `currentBsonType`.
     */
    private static class State {
        BsonType currentBsonType;
    }
}
