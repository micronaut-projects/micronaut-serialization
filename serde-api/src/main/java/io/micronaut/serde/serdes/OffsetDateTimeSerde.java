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
package io.micronaut.serde.serdes;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalQuery;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import jakarta.inject.Singleton;

/**
 * Serde for OffsetDateTime.
 */
@Singleton
public class OffsetDateTimeSerde implements TemporalSerde<OffsetDateTime> {
    @Override
    public void serialize(Encoder encoder, EncoderContext context, OffsetDateTime value, Argument<? extends OffsetDateTime> type)
            throws IOException {
        encoder.encodeLong(
                value.withOffsetSameInstant(ZoneOffset.UTC)
                        .toInstant().toEpochMilli()
        );
    }

    @Override
    public TemporalQuery<OffsetDateTime> query() {
        return OffsetDateTime::from;
    }

    @Override
    public OffsetDateTime deserializeNonNull(Decoder decoder,
                                             DecoderContext decoderContext,
                                             Argument<? super OffsetDateTime> type) throws IOException {
        return OffsetDateTime.ofInstant(
                Instant.ofEpochMilli(decoder.decodeLong()),
                UTC
        );
    }
}
