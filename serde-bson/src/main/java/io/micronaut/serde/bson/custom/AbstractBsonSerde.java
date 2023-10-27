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
import io.micronaut.serde.Decoder;
import io.micronaut.serde.DelegatingDecoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serde;
import io.micronaut.serde.bson.BsonReaderDecoder;
import io.micronaut.serde.bson.BsonWriterEncoder;
import io.micronaut.serde.exceptions.SerdeException;

import java.io.IOException;

/**
 * Abstract serializer/deserializer that needs to access Bson decoder/encoder.
 * @param <T>
 */
public abstract class AbstractBsonSerde<T> implements Serde<T> {

    protected abstract T doDeserialize(BsonReaderDecoder decoder, DecoderContext decoderContext, Argument<? super T> type) throws IOException;

    protected abstract void doSerialize(BsonWriterEncoder encoder, EncoderContext context, T value, Argument<? extends T> type) throws IOException;

    @Override
    public final T deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super T> type) throws IOException {
        return doDeserialize(asBson(decoder), decoderContext, type);
    }

    @Override
    public final void serialize(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException {
        doSerialize(asBson(encoder), context, value, type);
    }

    private BsonReaderDecoder asBson(Decoder decoder) throws IOException {
        if (decoder instanceof DelegatingDecoder delegating) {
            decoder = delegating.delegateForDecodeValue();
        }
        if (decoder instanceof BsonReaderDecoder bson) {
            return bson;
        }
        throw new SerdeException("Expected an instance of BsonParserDecoder got: " + decoder);
    }

    private BsonWriterEncoder asBson(Encoder encoder) throws SerdeException {
        if (encoder instanceof BsonWriterEncoder bsonWriterEncoder) {
            return bsonWriterEncoder;
        }
        throw new SerdeException("Expected an instance of BsonWriterEncoder got: " + encoder);
    }

}
