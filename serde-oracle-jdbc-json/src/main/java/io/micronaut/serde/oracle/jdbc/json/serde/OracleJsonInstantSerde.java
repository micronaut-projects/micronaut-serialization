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

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.oracle.jdbc.json.OracleJdbcJsonGeneratorEncoder;
import io.micronaut.serde.oracle.jdbc.json.OracleJdbcJsonParserDecoder;
import io.micronaut.serde.oracle.jdbc.json.annotation.OracleType;
import io.micronaut.serde.support.serdes.InstantSerde;
import io.micronaut.serde.util.NullableSerde;
import jakarta.inject.Singleton;

/**
 * Serde for {@link Instant} from Oracle JSON. It is needed since {@link oracle.sql.json.OracleJsonParser}
 * does not return {@link Instant} so we need to convert it from {@link LocalDateTime}.
 *
 * @author radovanradic
 * @since 2.0.0
 */
@Singleton
@Order(-100)
public class OracleJsonInstantSerde extends OracleJsonTemporalSerde<Instant> {

    private final InstantSerde instantSerde;

    public OracleJsonInstantSerde(InstantSerde instantSerde) {
        this.instantSerde = instantSerde;
    }

    @Override
    @NonNull
    protected Instant doDeserializeNonNull(@NonNull OracleJdbcJsonParserDecoder decoder,
                                           @NonNull DecoderContext decoderContext,
                                           @NonNull Argument<? super Instant> type) throws IOException {
        OracleType.Type t = type.getAnnotationMetadata().enumValue(OracleType.class, OracleType.Type.class).orElse(null);
        if (t == OracleType.Type.TEMPORAL) {
            LocalDateTime localDateTime = (LocalDateTime) decoder.decodeTemporal();
            return localDateTime.atZone(ZoneId.systemDefault()).toInstant();
        } else {
            return getDefault().deserializeNonNull(decoder, decoderContext, type);
        }
    }

    @Override
    protected void doSerializeNonNull(OracleJdbcJsonGeneratorEncoder encoder, EncoderContext context, Argument<? extends Instant> type, Instant value) throws IOException {
        OracleType.Type t = type.getAnnotationMetadata().enumValue(OracleType.class, OracleType.Type.class).orElse(null);
        if (t == OracleType.Type.TEMPORAL) {
            encoder.encodeString(LocalDateTime.ofInstant(value, ZoneId.systemDefault()).toString());
        } else {
            getDefault().serialize(encoder, context, type, value);
        }
    }

    @Override
    protected NullableSerde<Instant> getDefault() {
        return instantSerde;
    }
}
