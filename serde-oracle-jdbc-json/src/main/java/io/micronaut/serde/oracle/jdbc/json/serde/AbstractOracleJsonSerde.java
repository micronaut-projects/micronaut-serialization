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
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serde;
import io.micronaut.serde.WrappedDecoder;
import io.micronaut.serde.WrappedEncoder;
import io.micronaut.serde.oracle.jdbc.json.OracleJdbcJsonGeneratorEncoder;
import io.micronaut.serde.oracle.jdbc.json.OracleJdbcJsonParserDecoder;
import io.micronaut.serde.oracle.jdbc.json.OracleOsonDecoder;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

/**
 * Abstract serializer/deserializer that needs to access Oracle JSON decoder.
 *
 * @param <T> the type being deserialized
 */
@Internal
public abstract class AbstractOracleJsonSerde<T> implements Serde<T> {

    @Override
    public final T deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super T> type) throws IOException {
        return switch (WrappedDecoder.unwrap(decoder)) {
            case OracleJdbcJsonParserDecoder parserDecoder -> doDeserializeNonNull(parserDecoder, decoderContext, type);
            case OracleOsonDecoder osonDecoder -> doDeserializeNonNull(osonDecoder, decoderContext, type);
            default -> getDefault().deserialize(decoder, decoderContext, type);
        };
    }

    @Override
    public final @Nullable T deserializeNullable(Decoder decoder, DecoderContext decoderContext, Argument<? super T> type) throws IOException {
        Decoder unwrappedDecoder = WrappedDecoder.unwrap(decoder);
        boolean oracleDecoder = unwrappedDecoder instanceof OracleJdbcJsonParserDecoder || unwrappedDecoder instanceof OracleOsonDecoder;
        if (!oracleDecoder) {
            return getDefault().deserializeNullable(decoder, decoderContext, type);
        }
        if (decoder.decodeNull()) {
            return null;
        }
        return deserialize(decoder, decoderContext, type);
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException {
        Encoder unwrappedEncoder = WrappedEncoder.unwrap(encoder);
        if (unwrappedEncoder instanceof OracleJdbcJsonGeneratorEncoder oracleEncoder) {
            doSerializeNonNull(oracleEncoder, context, type, value);
        } else {
            getDefault().serialize(encoder, context, type, value);
        }
    }

    /**
     * Deserializes object using {@link OracleJdbcJsonParserDecoder}.
     *
     * @param decoder        the Oracle JSON decoder
     * @param decoderContext the decoder context
     * @param type           the type being deserialized
     * @return the deserialized instance of given type
     * @throws IOException if an unrecoverable error occurs
     */
    protected abstract T doDeserializeNonNull(OracleJdbcJsonParserDecoder decoder, DecoderContext decoderContext,
                                              Argument<? super T> type) throws IOException;

    /**
     * Deserializes object using the native {@link OracleOsonDecoder}. The default implementation falls back to
     * {@link #getDefault()}; the serdes of this module override it to read the Oracle type directly.
     *
     * @param decoder        the OSON decoder
     * @param decoderContext the decoder context
     * @param type           the type being deserialized
     * @return the deserialized instance of given type
     * @throws IOException if an unrecoverable error occurs
     * @since 3.3.0
     */
    protected T doDeserializeNonNull(OracleOsonDecoder decoder, DecoderContext decoderContext,
                                     Argument<? super T> type) throws IOException {
        return getDefault().deserialize(decoder, decoderContext, type);
    }

    /**
     * Serializes non null value.
     *
     * @param encoder the encoder
     * @param context the encoder context
     * @param type    the type of object being serialized
     * @param value   the value being serialized
     * @throws IOException if an unrecoverable error occurs
     */
    protected abstract void doSerializeNonNull(OracleJdbcJsonGeneratorEncoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException;

    /**
     * @return The default behaviour
     */
    protected abstract Serde<T> getDefault();
}
