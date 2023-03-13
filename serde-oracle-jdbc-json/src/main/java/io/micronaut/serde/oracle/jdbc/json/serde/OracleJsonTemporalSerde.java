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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.oracle.jdbc.json.OracleJdbcJsonGeneratorEncoder;
import io.micronaut.serde.oracle.jdbc.json.OracleJdbcJsonParserDecoder;
import io.micronaut.serde.oracle.jdbc.json.annotation.OracleType;
import io.micronaut.serde.util.NullableSerde;

import java.io.IOException;
import java.time.temporal.Temporal;

/**
 * The base class for {@link Temporal} serde in Oracle JSON.
 *
 * @author radovanradic
 * @since 2.0.0
 *
 * @param <T> the temporal type to be de/serialized
 */
public abstract class OracleJsonTemporalSerde<T extends Temporal> extends AbstractOracleJsonSerde<T> implements NullableSerde<T> {

    @SuppressWarnings("unchecked")
    @Override
    @NonNull
    protected T doDeserializeNonNull(@NonNull OracleJdbcJsonParserDecoder decoder, @NonNull DecoderContext decoderContext,
                                     @NonNull Argument<? super T> type) throws IOException {
        OracleType.Type t = type.getAnnotationMetadata().enumValue(OracleType.class, OracleType.Type.class).orElse(null);
        if (t == OracleType.Type.TEMPORAL) {
            Temporal value = decoder.decodeTemporal();
            return (T) value;
        } else {
            return getDefault().deserializeNonNull(decoder, decoderContext, type);
        }
    }

    @Override
    protected void doSerializeNonNull(@NonNull OracleJdbcJsonGeneratorEncoder encoder, @NonNull EncoderContext context, @NonNull Argument<? extends T> type, @NonNull T value) throws IOException {
        OracleType.Type t = type.getAnnotationMetadata().enumValue(OracleType.class, OracleType.Type.class).orElse(null);
        if (t == OracleType.Type.TEMPORAL) {
            encoder.encodeString(value.toString());
        } else {
            getDefault().serialize(
                encoder,
                context,
                type,
                value
            );
        }
    }
}
