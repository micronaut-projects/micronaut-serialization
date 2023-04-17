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
import io.micronaut.serde.bson.BsonReaderDecoder;
import io.micronaut.serde.bson.BsonWriterEncoder;
import org.bson.codecs.Codec;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Serializer/deserializer implemented by {@link Codec}.
 *
 * @param <T>
 */
@Internal
public class CodecBsonDecoder<T> extends AbstractBsonSerde<T> {

    private static final org.bson.codecs.DecoderContext DEFAULT_DECODER_CONTEXT = org.bson.codecs.DecoderContext.builder().build();
    private static final org.bson.codecs.EncoderContext DEFAULT_ENCODER_CONTEXT = org.bson.codecs.EncoderContext.builder().build();

    private final Codec<T> codec;

    public CodecBsonDecoder(Codec<T> codec) {
        this.codec = codec;
    }

    @Override
    protected T doDeserialize(BsonReaderDecoder decoder, DecoderContext decoderContext, Argument<? super T> type) {
        try {
            return decoder.decodeCustom(codec, DEFAULT_DECODER_CONTEXT);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    protected void doSerialize(BsonWriterEncoder encoder, EncoderContext context, T value, Argument<? extends T> type) {
        codec.encode(encoder.getBsonWriter(), value, DEFAULT_ENCODER_CONTEXT);
    }
}
