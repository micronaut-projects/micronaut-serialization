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

/**
 * Bson implementation of {@link Decoder}.
 *
 * @author Denis Stepanov
 */
@Internal
public final class BsonReaderDecoder implements Decoder {
    private final BsonReader bsonReader;
    private final boolean inArray;
    private boolean isEndOfDocument;

    public BsonReaderDecoder(BsonReader bsonReader) {
        this(bsonReader, false);
    }

    public BsonReaderDecoder(BsonReader bsonReader, boolean inArray) {
        this.bsonReader = bsonReader;
        this.inArray = inArray;
        next();
    }

    private void next() {
        BsonType bsonType = bsonReader.readBsonType();
        isEndOfDocument = bsonType == BsonType.END_OF_DOCUMENT;
        System.out.println(bsonType + " " + isEndOfDocument());
    }

    @Override
    public Decoder decodeArray() throws IOException {
        if (bsonReader.getCurrentBsonType() == BsonType.ARRAY) {
            bsonReader.readStartArray();
        } else {
            throw createDeserializationException("Not an array");
        }
        return new BsonReaderDecoder(bsonReader, true);
    }

    @Override
    public boolean hasNextArrayValue() {
        if (!inArray) {
            return false;
        }
        if (isEndOfDocument()) {
            next();
            return !isEndOfDocument();
        }
        return true;
    }

    private boolean isEndOfDocument() {
        return isEndOfDocument || bsonReader.getCurrentBsonType() == BsonType.END_OF_DOCUMENT;
    }

    @Override
    public Decoder decodeObject() throws IOException {
        if (bsonReader.getCurrentBsonType() == BsonType.DOCUMENT) {
            bsonReader.readStartDocument();
        } else {
            throw createDeserializationException("Not an object");
        }
        return new BsonReaderDecoder(bsonReader);
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
        if (bsonReader.getCurrentBsonType() == BsonType.STRING) {
            try {
                return bsonReader.readString();
            } finally {
                next();
            }
        }
        throw createDeserializationException("Cannot decode String from: " + bsonReader.getCurrentBsonType());
    }

    @Override
    public boolean decodeBoolean() throws IOException {
        switch (bsonReader.getCurrentBsonType()) {
            case NULL:
                try {
                    bsonReader.readNull();
                    return false;
                } finally {
                    next();
                }
            case BOOLEAN:
                try {
                    return bsonReader.readBoolean();
                } finally {
                    next();
                }
            default:
                throw createDeserializationException("Cannot decode Boolean from: " + bsonReader.getCurrentBsonType());
        }
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
        switch (bsonReader.getCurrentBsonType()) {
            case NULL:
                try {
                    bsonReader.readNull();
                    return 0;
                } finally {
                    next();
                }
            case INT32:
                try {
                    return bsonReader.readInt32();
                } finally {
                    next();
                }
            default:
                throw createDeserializationException("Cannot decode int from: " + bsonReader.getCurrentBsonType());
        }
    }

    @Override
    public long decodeLong() throws IOException {
        switch (bsonReader.getCurrentBsonType()) {
            case NULL:
                try {
                    bsonReader.readNull();
                    return 0L;
                } finally {
                    next();
                }
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
                throw createDeserializationException("Cannot decode Long from: " + bsonReader.getCurrentBsonType());
        }
    }

    @Override
    public float decodeFloat() throws IOException {
        if (bsonReader.getCurrentBsonType() == BsonType.NULL) {
            try {
                bsonReader.readNull();
                return 0f;
            } finally {
                next();
            }
        }
        if (bsonReader.getCurrentBsonType() == BsonType.DOUBLE) {
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
        if (bsonReader.getCurrentBsonType() == BsonType.NULL) {
            try {
                bsonReader.readNull();
                return 0d;
            } finally {
                next();
            }
        }
        if (bsonReader.getCurrentBsonType() == BsonType.DOUBLE) {
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
        switch (bsonReader.getCurrentBsonType()) {
            case INT32:
                return BigInteger.valueOf(decodeInt());
            case INT64:
                return BigInteger.valueOf(decodeLong());
            case DECIMAL128:
                return decodeDecimal128().bigDecimalValue().toBigInteger();
            default:
                throw createDeserializationException("Cannot decode BigInteger from: " + bsonReader.getCurrentBsonType());
        }
    }

    @Override
    public BigDecimal decodeBigDecimal() throws IOException {
        switch (bsonReader.getCurrentBsonType()) {
            case INT32:
                return BigDecimal.valueOf(decodeInt());
            case INT64:
                return BigDecimal.valueOf(decodeLong());
            case DOUBLE:
                return BigDecimal.valueOf(decodeDouble());
            case DECIMAL128:
                return decodeDecimal128().bigDecimalValue();
            default:
                throw createDeserializationException("Cannot decode BigDecimal from: " + bsonReader.getCurrentBsonType());
        }
    }

    @Override
    public boolean decodeNull() {
        if (bsonReader.getCurrentBsonType() == BsonType.NULL) {
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
            throw createDeserializationException("Expected END_OF_DOCUMENT got: " + bsonReader.getCurrentBsonType());
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
        if (bsonReader.getCurrentBsonType() == BsonType.DECIMAL128) {
            try {
                return bsonReader.readDecimal128();
            } finally {
                next();
            }
        }
        throw createDeserializationException("Cannot decode Decimal128 from: " + bsonReader.getCurrentBsonType());
    }
}
