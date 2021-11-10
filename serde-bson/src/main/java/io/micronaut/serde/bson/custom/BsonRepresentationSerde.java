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
package io.micronaut.serde.bson.custom;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.bson.BsonReaderDecoder;
import io.micronaut.serde.bson.BsonWriterEncoder;
import io.micronaut.serde.exceptions.SerdeException;
import jakarta.inject.Singleton;
import org.bson.BsonBinary;
import org.bson.BsonDbPointer;
import org.bson.BsonReader;
import org.bson.BsonRegularExpression;
import org.bson.BsonTimestamp;
import org.bson.BsonType;
import org.bson.BsonWriter;
import org.bson.codecs.pojo.annotations.BsonRepresentation;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Custom {@link BsonType} representation serializer/deserializer.
 *
 * @author Denis Stepanov
 */
@Singleton
public final class BsonRepresentationSerde extends AbstractBsonSerder<Object> {

    @Override
    protected Object doDeserializeNonNull(BsonReaderDecoder decoder, DecoderContext context, Argument<? super Object> type) throws IOException {
        BsonType bsonType = getBsonType(type);
        return decoder.decodeCustom(p -> {
            BsonReader bsonReader = decoder.getBsonReader();
            switch (bsonType) {
                case DOUBLE:
                    return convert(context, type, bsonReader.readDouble());
                case STRING:
                    if (type.getType().equals(ObjectId.class)) {
                        return new ObjectId(bsonReader.readString());
                    }
                    return convert(context, type, bsonReader.readString());
                case JAVASCRIPT:
                    return convert(context, type, bsonReader.readJavaScript());
                case SYMBOL:
                    return convert(context, type, bsonReader.readSymbol());
                case JAVASCRIPT_WITH_SCOPE:
                    return convert(context, type, bsonReader.readJavaScriptWithScope());
                case BINARY:
                    if (type.getType().equals(byte[].class)) {
                        return bsonReader.readBinaryData().getData();
                    }
                    if (type.getType().equals(UUID.class)) {
                        return bsonReader.readBinaryData().asUuid();
                    }
                    return convert(context, type, bsonReader.readBinaryData());
                case OBJECT_ID:
                    return convert(context, type, bsonReader.readObjectId());
                case BOOLEAN:
                    return convert(context, type, bsonReader.readBoolean());
                case DATE_TIME:
                    return convert(context, type, bsonReader.readDateTime());
                case REGULAR_EXPRESSION:
                    return convert(context, type, bsonReader.readRegularExpression());
                case DB_POINTER:
                    return convert(context, type, bsonReader.readDBPointer());
                case INT32:
                    return convert(context, type, bsonReader.readInt32());
                case TIMESTAMP:
                    return convert(context, type, bsonReader.readTimestamp());
                case INT64:
                    return convert(context, type, bsonReader.readInt64());
                case DECIMAL128:
                    return convert(context, type, bsonReader.readDecimal128());
                default:
                    throw new SerdeException("Unsupported BsonType: " + bsonType);
            }
        });
    }

    private Object convert(DecoderContext context, Argument<? super Object> type, Object value) {
        if (type.isInstance(value)) {
            return value;
        }
        return context.getConversionService().convertRequired(value, type);
    }

    @Override
    protected void doSerialize(BsonWriterEncoder encoder, EncoderContext context, Object value, Argument<?> type) throws IOException {
        if (value == null) {
            encoder.encodeNull();
        } else {
            BsonWriter bsonWriter = encoder.getBsonWriter();
            BsonType bsonType = getBsonType(type);
            switch (bsonType) {
                case DOUBLE:
                    bsonWriter.writeDouble(convert(context, value, Double.class));
                    break;
                case STRING:
                    if (value instanceof ObjectId) {
                        bsonWriter.writeString(((ObjectId) value).toHexString());
                    } else {
                        bsonWriter.writeString(convert(context, value, String.class));
                    }
                    break;
                case BINARY:
                    if (value instanceof byte[]) {
                        bsonWriter.writeBinaryData(new BsonBinary((byte[]) value));
                    } else if (value instanceof UUID) {
                        bsonWriter.writeBinaryData(new BsonBinary((UUID) value));
                    } else {
                        bsonWriter.writeBinaryData(convert(context, value, BsonBinary.class));
                    }
                    break;
                case OBJECT_ID:
                    if (value instanceof String) {
                        bsonWriter.writeObjectId(new ObjectId((String) value));
                    } else {
                        bsonWriter.writeObjectId(convert(context, value, ObjectId.class));
                    }
                    break;
                case BOOLEAN:
                    bsonWriter.writeBoolean(convert(context, value, Boolean.class));
                    break;
                case DATE_TIME:
                    if (value instanceof Long) {
                        bsonWriter.writeDateTime((Long) value);
                    } else {
                        bsonWriter.writeDateTime(convert(context, value, Instant.class).getEpochSecond());
                    }
                    break;
                case REGULAR_EXPRESSION:
                    bsonWriter.writeRegularExpression(convert(context, value, BsonRegularExpression.class));
                    break;
                case DB_POINTER:
                    bsonWriter.writeDBPointer(convert(context, value, BsonDbPointer.class));
                    break;
                case JAVASCRIPT:
                    bsonWriter.writeJavaScript(convert(context, value, String.class));
                    break;
                case SYMBOL:
                    bsonWriter.writeSymbol(convert(context, value, String.class));
                    break;
                case JAVASCRIPT_WITH_SCOPE:
                    bsonWriter.writeJavaScriptWithScope(convert(context, value, String.class));
                    break;
                case INT32:
                    bsonWriter.writeInt32(convert(context, value, Integer.class));
                    break;
                case TIMESTAMP:
                    bsonWriter.writeTimestamp(convert(context, value, BsonTimestamp.class));
                    break;
                case INT64:
                    bsonWriter.writeInt64(convert(context, value, Long.class));
                    break;
                case DECIMAL128:
                    if (value instanceof BigDecimal) {
                        bsonWriter.writeDecimal128(new Decimal128((BigDecimal) value));
                    } else {
                        bsonWriter.writeDecimal128(convert(context, value, Decimal128.class));
                    }
                    break;
                default:
                    throw new SerdeException("Unsupported BsonType: " + bsonType);
            }
        }
    }

    private <T> T convert(EncoderContext context, Object value, Class<T> clazz) {
        if (clazz.isInstance(value)) {
            return (T) value;
        }
        return context.getConversionService().convertRequired(value, clazz);
    }

    private BsonType getBsonType(Argument<?> type) throws SerdeException {
        return type.getAnnotationMetadata().enumValue(BsonRepresentation.class, BsonType.class).orElseThrow(() -> new SerdeException("BsonType is expected for @BsonRepresentation!"));
    }
}
