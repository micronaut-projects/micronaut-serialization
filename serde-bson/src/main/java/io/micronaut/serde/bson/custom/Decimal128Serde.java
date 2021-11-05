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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.bson.BsonReaderDecoder;
import io.micronaut.serde.bson.BsonWriterEncoder;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.util.NullableDeserializer;
import jakarta.inject.Singleton;
import org.bson.BsonBinaryWriter;
import org.bson.types.Decimal128;

import java.io.IOException;

/**
 * Serializer and deserializer of {@link Decimal128}.
 *
 * @author Denis Stepanov
 */
@Singleton
@Internal
public final class Decimal128Serde implements Serializer<Decimal128>, NullableDeserializer<Decimal128> {

    @Override
    public Decimal128 deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super Decimal128> type) throws IOException {
        return asBson(decoder).decodeDecimal128();
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Decimal128 value, Argument<? extends Decimal128> type) throws IOException {
        asBson(encoder).encodeDecimal128(value);
    }

    private BsonReaderDecoder asBson(Decoder decoder) throws SerdeException {
        if (decoder instanceof BsonReaderDecoder) {
            return (BsonReaderDecoder) decoder;
        }
        throw new SerdeException("Expected an instance of BsonParserDecoder got: " + decoder);
    }

    private BsonWriterEncoder asBson(Encoder encoder) throws SerdeException {
        if (encoder instanceof BsonWriterEncoder) {
            return (BsonWriterEncoder) encoder;
        }
        throw new SerdeException("Expected an instance of BsonWriterEncoder got: " + encoder);
    }
}
