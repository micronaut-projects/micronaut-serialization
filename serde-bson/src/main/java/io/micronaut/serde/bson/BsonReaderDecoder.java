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
import io.micronaut.serde.Decoder;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.AbstractDecoderPerStructureStreamDecoder;
import io.micronaut.serde.support.AbstractStreamDecoder;
import org.bson.BsonBinaryReader;
import org.bson.BsonBinaryWriter;
import org.bson.BsonReader;
import org.bson.BsonType;
import org.bson.BsonWriter;
import org.bson.codecs.DecoderContext;
import org.bson.io.BasicOutputBuffer;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Bson implementation of {@link Decoder}.
 *
 * @author Denis Stepanov
 */
@Internal
public final class BsonReaderDecoder extends AbstractDecoderPerStructureStreamDecoder {
    private final BsonReader bsonReader;
    private final Deque<Context> contextStack;

    private BsonType currentBsonType;
    private TokenType currentToken;

    public BsonReaderDecoder(BsonReader bsonReader, @NonNull RemainingLimits remainingLimits) {
        super(remainingLimits);
        this.bsonReader = bsonReader;
        this.contextStack = new ArrayDeque<>();
        BsonType currentBsonType = bsonReader.getCurrentBsonType();
        if (currentBsonType == null) {
            this.contextStack.add(Context.TOP);
            nextToken();
        } else if (currentBsonType == BsonType.DOCUMENT) {
            this.contextStack.push(Context.TOP);
            currentToken = TokenType.START_OBJECT;
            this.currentBsonType = BsonType.DOCUMENT;
        }
    }

    private BsonReaderDecoder(BsonReaderDecoder parent, @NonNull RemainingLimits remainingLimits) {
        super(parent, remainingLimits);
        this.bsonReader = parent.bsonReader;
        this.contextStack = parent.contextStack;
        this.currentBsonType = parent.currentBsonType;
        this.currentToken = parent.currentToken;
    }

    private enum Context {
        ARRAY,
        DOCUMENT,
        TOP,
    }

    @Override
    protected void backFromChild(AbstractStreamDecoder child) throws IOException {
        this.currentBsonType = ((BsonReaderDecoder) child).currentBsonType;
        this.currentToken = ((BsonReaderDecoder) child).currentToken;
        super.backFromChild(child);
    }

    @Override
    protected void nextToken() {
        if (currentToken != null) {
            switch (currentToken) {
                case START_ARRAY -> {
                    contextStack.push(Context.ARRAY);
                    bsonReader.readStartArray();
                }
                case START_OBJECT -> {
                    contextStack.push(Context.DOCUMENT);
                    bsonReader.readStartDocument();
                }
                case END_ARRAY -> {
                    contextStack.pop();
                    bsonReader.readEndArray();
                }
                case END_OBJECT -> {
                    contextStack.pop();
                    bsonReader.readEndDocument();
                }
                case NULL -> bsonReader.readNull();
                default -> {
                }
            }
        }

        Context ctx = contextStack.peek();
        if (ctx == Context.DOCUMENT) {
            if (currentToken == TokenType.KEY) {
                // move into the value
                currentToken = toToken(currentBsonType, ctx);
            } else {
                currentBsonType = bsonReader.readBsonType();
                if (currentBsonType == BsonType.END_OF_DOCUMENT) {
                    currentToken = TokenType.END_OBJECT;
                } else {
                    currentToken = TokenType.KEY;
                }
            }
        } else {
            if (ctx != Context.TOP || currentBsonType != BsonType.END_OF_DOCUMENT) {
                currentBsonType = bsonReader.readBsonType();
                currentToken = toToken(currentBsonType, ctx);
            }
        }
    }

    private static TokenType toToken(BsonType bsonType, Context ctx) {
        switch (bsonType) {
            case ARRAY:
                return TokenType.START_ARRAY;
            case DOCUMENT:
                return TokenType.START_OBJECT;
            case END_OF_DOCUMENT:
                if (ctx == Context.ARRAY) {
                    return TokenType.END_ARRAY;
                } else if (ctx == Context.DOCUMENT) {
                    return TokenType.END_OBJECT;
                } else {
                    // EOF
                    return null;
                }
            case DOUBLE:
            case INT32:
            case INT64:
            case DECIMAL128:
                return TokenType.NUMBER;
            case STRING:
                return TokenType.STRING;
            case BOOLEAN:
                return TokenType.BOOLEAN;
            case NULL:
                return TokenType.NULL;
            case BINARY:
            case UNDEFINED:
            case OBJECT_ID:
            case DATE_TIME:
            case REGULAR_EXPRESSION:
            case DB_POINTER:
            case JAVASCRIPT:
            case SYMBOL:
            case JAVASCRIPT_WITH_SCOPE:
            case TIMESTAMP:
            case MIN_KEY:
            case MAX_KEY:
            default:
                return TokenType.OTHER;
        }
    }

    @Override
    protected String getCurrentKey() {
        return bsonReader.readName();
    }

    @Override
    protected String coerceScalarToString(TokenType currentToken) throws IOException {
        return switch (currentBsonType) {
            case DOUBLE -> String.valueOf(bsonReader.readDouble());
            case STRING -> bsonReader.readString();
            case OBJECT_ID -> bsonReader.readObjectId().toHexString();
            case BOOLEAN -> String.valueOf(bsonReader.readBoolean());
            case DATE_TIME -> String.valueOf(bsonReader.readDateTime());
            case REGULAR_EXPRESSION -> bsonReader.readRegularExpression().toString();
            case JAVASCRIPT -> bsonReader.readJavaScript();
            case SYMBOL -> bsonReader.readSymbol();
            case JAVASCRIPT_WITH_SCOPE -> bsonReader.readJavaScriptWithScope();
            case INT32 -> String.valueOf(bsonReader.readInt32());
            case TIMESTAMP -> bsonReader.readTimestamp().toString();
            case INT64 -> String.valueOf(bsonReader.readInt64());
            case DECIMAL128 -> bsonReader.readDecimal128().toString();
            case BINARY -> new String(bsonReader.readBinaryData().getData(), StandardCharsets.UTF_8);
            case DB_POINTER -> bsonReader.readDBPointer().toString();
            default -> throw new SerdeException("Can't decode " + currentBsonType + " as string");
        };
    }

    @Override
    protected AbstractStreamDecoder createChildDecoder() throws SerdeException {
        return new BsonReaderDecoder(this, childLimits());
    }

    @Override
    protected String getString() {
        return bsonReader.readString();
    }

    @Override
    protected boolean getBoolean() {
        return bsonReader.readBoolean();
    }

    @Override
    protected long getLong() {
        return switch (currentBsonType) {
            case INT32 -> bsonReader.readInt32();
            case INT64 -> bsonReader.readInt64();
            case DOUBLE -> (long) bsonReader.readDouble();
            case DECIMAL128 -> bsonReader.readDecimal128().longValue();
            default -> throw getNotInNumberState();
        };
    }

    private IllegalStateException getNotInNumberState() {
        return new IllegalStateException("Not in number state");
    }

    @Override
    protected double getDouble() {
        return switch (currentBsonType) {
            case INT32 -> bsonReader.readInt32();
            case INT64 -> bsonReader.readInt64();
            case DOUBLE -> bsonReader.readDouble();
            case DECIMAL128 -> bsonReader.readDecimal128().doubleValue();
            default -> throw getNotInNumberState();
        };
    }

    @Override
    protected BigInteger getBigInteger() {
        return switch (currentBsonType) {
            case INT32 -> BigInteger.valueOf(bsonReader.readInt32());
            case INT64 -> BigInteger.valueOf(bsonReader.readInt64());
            case DOUBLE -> BigDecimal.valueOf(bsonReader.readDouble()).toBigInteger();
            case DECIMAL128 -> bsonReader.readDecimal128().bigDecimalValue().toBigInteger();
            default -> throw getNotInNumberState();
        };
    }

    @Override
    protected BigDecimal getBigDecimal() {
        return switch (currentBsonType) {
            case INT32 -> BigDecimal.valueOf(bsonReader.readInt32());
            case INT64 -> BigDecimal.valueOf(bsonReader.readInt64());
            case DOUBLE -> BigDecimal.valueOf(bsonReader.readDouble());
            case DECIMAL128 -> bsonReader.readDecimal128().bigDecimalValue();
            default -> throw getNotInNumberState();
        };
    }

    @Override
    protected Number getBestNumber() {
        return switch (currentBsonType) {
            case INT32 -> bsonReader.readInt32();
            case INT64 -> bsonReader.readInt64();
            case DOUBLE -> bsonReader.readDouble();
            case DECIMAL128 -> bsonReader.readDecimal128();
            default -> throw getNotInNumberState();
        };
    }

    @Override
    public byte @NonNull [] decodeBinary() throws IOException {
        if (currentBsonType == BsonType.BINARY) {
            return decodeCustom(parser -> ((BsonReaderDecoder) parser).bsonReader.readBinaryData().getData());
        } else {
            return super.decodeBinary();
        }
    }

    @Override
    protected void skipChildren() {
        bsonReader.skipValue();
        currentToken = null;
    }

    @Override
    protected TokenType currentToken() {
        return currentToken;
    }

    @Override
    public IOException createDeserializationException(String message, Object invalidValue) {
        return new SerdeException(message + " \n at ");
    }

    private Decimal128 getDecimal128() {
        return switch (currentBsonType) {
            case INT32 -> new Decimal128(bsonReader.readInt32());
            case INT64 -> new Decimal128(bsonReader.readInt64());
            case DOUBLE -> new Decimal128(BigDecimal.valueOf(bsonReader.readDouble()));
            case DECIMAL128 -> bsonReader.readDecimal128();
            default -> throw getNotInNumberState();
        };
    }

    /**
     * Decodes {@link Decimal128}.
     *
     * @return decoded value
     * @throws IOException
     */
    public Decimal128 decodeDecimal128() throws IOException {
        return decodeNumber(currentToken(), decoder -> ((BsonReaderDecoder) decoder).getDecimal128(), Decimal128::parse, Decimal128.POSITIVE_ZERO, new Decimal128(1));
    }

    /**
     * Decodes {@link ObjectId}.
     *
     * @return decoded value
     * @throws IOException
     */
    public ObjectId decodeObjectId() throws IOException {
        if (currentBsonType != BsonType.OBJECT_ID) {
            throw createDeserializationException("Cannot decode ObjectId from: " + currentBsonType, decodeArbitrary());
        }
        return decodeCustom(parser -> ((BsonReaderDecoder) parser).bsonReader.readObjectId());
    }

    public <T> T decodeCustom(org.bson.codecs.Decoder<T> decoder, DecoderContext context) throws IOException {
        currentToken = null;
        currentBsonType = null;
        T result = decodeCustom(p -> decoder.decode(bsonReader, context), false);
        Context ctx = contextStack.peek();
        if (ctx == Context.TOP) {
            return result;
        }
        nextToken();
        return result;
    }

    /**
     * Copy the current value to a bson document containing a single element with name {@code ""} and the value.
     */
    private byte[] copyValueToDocument() {
        BasicOutputBuffer buffer = new BasicOutputBuffer();
        try (BsonBinaryWriter writer = new BsonBinaryWriter(buffer)) {
            // have to wrap in a document
            writer.writeStartDocument();
            writer.writeName("");
            transfer(bsonReader, writer, currentBsonType);
            writer.writeEndDocument();
        }

        currentToken = null;
        currentBsonType = null;
        return buffer.getInternalBuffer();
    }

    private Decoder decoderFromBytes(byte[] documentBytes) throws IOException {
        BsonReaderDecoder topDecoder = new BsonReaderDecoder(new BsonBinaryReader(ByteBuffer.wrap(documentBytes)), ourLimits());
        Decoder decoder = topDecoder.decodeObject();
        decoder.decodeKey(); // Unwrap
        return decoder;
    }

    @Override
    public Decoder decodeBuffer() throws IOException {
        byte[] documentBytes = decodeCustom(p -> ((BsonReaderDecoder) p).copyValueToDocument());
        return decoderFromBytes(documentBytes);
    }

    private static void transfer(BsonReader src, BsonWriter dest, BsonType type) {
        switch (type) {
            case DOUBLE:
                dest.writeDouble(src.readDouble());
                break;
            case STRING:
                dest.writeString(src.readString());
                break;
            case DOCUMENT:
                src.readStartDocument();
                dest.writeStartDocument();
                while (src.readBsonType() != BsonType.END_OF_DOCUMENT) {
                    String name = src.readName();
                    if (name != null) {
                        dest.writeName(name);
                    } else {
                        break;
                    }
                    transfer(src, dest, src.getCurrentBsonType());
                }
                src.readEndDocument();
                dest.writeEndDocument();
                break;
            case ARRAY:
                src.readStartArray();
                dest.writeStartArray();
                while (true) {
                    BsonType elementType = src.readBsonType();
                    if (elementType == BsonType.END_OF_DOCUMENT) {
                        break;
                    }
                    transfer(src, dest, elementType);
                }
                src.readEndArray();
                dest.writeEndArray();
                break;
            case BINARY:
                dest.writeBinaryData(src.readBinaryData());
                break;
            case UNDEFINED:
                src.readUndefined();
                dest.writeUndefined();
                break;
            case OBJECT_ID:
                dest.writeObjectId(src.readObjectId());
                break;
            case BOOLEAN:
                dest.writeBoolean(src.readBoolean());
                break;
            case DATE_TIME:
                dest.writeDateTime(src.readDateTime());
                break;
            case NULL:
                src.readNull();
                dest.writeNull();
                break;
            case REGULAR_EXPRESSION:
                dest.writeRegularExpression(src.readRegularExpression());
                break;
            case DB_POINTER:
                dest.writeDBPointer(src.readDBPointer());
                break;
            case JAVASCRIPT:
                dest.writeJavaScript(src.readJavaScript());
                break;
            case SYMBOL:
                dest.writeSymbol(src.readSymbol());
                break;
            case JAVASCRIPT_WITH_SCOPE:
                dest.writeJavaScriptWithScope(src.readJavaScriptWithScope());
                break;
            case INT32:
                dest.writeInt32(src.readInt32());
                break;
            case TIMESTAMP:
                dest.writeTimestamp(src.readTimestamp());
                break;
            case INT64:
                dest.writeInt64(src.readInt64());
                break;
            case DECIMAL128:
                dest.writeDecimal128(src.readDecimal128());
                break;
            case MIN_KEY:
                src.readMinKey();
                dest.writeMinKey();
                break;
            case MAX_KEY:
                src.readMaxKey();
                dest.writeMaxKey();
                break;
            default:
                throw new IllegalStateException("Can't transfer bson token: " + type);
        }
    }
}
