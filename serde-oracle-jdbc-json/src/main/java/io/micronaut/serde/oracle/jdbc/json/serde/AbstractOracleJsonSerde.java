/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.serde.oracle.jdbc.json.serde;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serde;
import io.micronaut.serde.oracle.jdbc.json.OracleJdbcJsonGeneratorEncoder;
import io.micronaut.serde.oracle.jdbc.json.OracleJdbcJsonParserDecoder;

import java.io.IOException;

/**
 * Abstract serializer/deserializer that needs to access Oracle JSON decoder.
 *
 * @param <T> the type being deserialized
 */
@Internal
public abstract class AbstractOracleJsonSerde<T> implements Serde<T> {

    @Override
    @NonNull
    public final T deserialize(@NonNull Decoder decoder, @NonNull DecoderContext decoderContext, @NonNull Argument<? super T> type) throws IOException {
        if (decoder instanceof OracleJdbcJsonParserDecoder oracleJdbcJsonParserDecoder) {
            return doDeserializeNonNull(oracleJdbcJsonParserDecoder, decoderContext, type);
        } else {
            return getDefault().deserialize(decoder, decoderContext, type);
        }
    }

    @Override
    public void serialize(@NonNull Encoder encoder, @NonNull EncoderContext context, @NonNull Argument<? extends T> type, T value) throws IOException {
        if (encoder instanceof OracleJdbcJsonGeneratorEncoder oracleEncoder) {
            if (value == null) {
                encoder.encodeNull();
            } else {
                doSerializeNonNull(oracleEncoder, context, type, value);
            }
        } else {
            getDefault().serialize(encoder, context, type, value);
        }
    }

    /**
     * Deserializes object using {@link OracleJdbcJsonParserDecoder}.
     *
     * @param decoder the Oracle JSON decoder
     * @param decoderContext the decoder context
     * @param type the type being deserialized
     * @return the deserialized instance of given type
     * @throws IOException if an unrecoverable error occurs
     */
    @NonNull
    protected abstract T doDeserializeNonNull(@NonNull OracleJdbcJsonParserDecoder decoder, @NonNull DecoderContext decoderContext,
                                              @NonNull Argument<? super T> type) throws IOException;

    /**
     * Serializes non null value.
     *
     * @param encoder the encoder
     * @param context the encoder context
     * @param type the type of object being serialized
     * @param value the value being serialized
     * @throws IOException if an unrecoverable error occurs
     */
    protected abstract void doSerializeNonNull(OracleJdbcJsonGeneratorEncoder encoder, EncoderContext context, Argument<? extends T> type, @NonNull T value) throws IOException;

    /**
     * @return The default behaviour
     */
    protected abstract Serde<T> getDefault();
}
