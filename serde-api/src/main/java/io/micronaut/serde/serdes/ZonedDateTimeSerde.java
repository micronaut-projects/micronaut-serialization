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
import java.time.ZonedDateTime;
import java.time.temporal.TemporalQuery;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import jakarta.inject.Singleton;

/**
 * Zoned date time serde.
 *
 * @since 1.0.0
 */
@Singleton
public class ZonedDateTimeSerde implements TemporalSerde<ZonedDateTime> {

    @Override
    public void serialize(Encoder encoder, EncoderContext context, ZonedDateTime value, Argument<? extends ZonedDateTime> type)
            throws IOException {
        encoder.encodeLong(value.withZoneSameInstant(UTC).toInstant().toEpochMilli());
    }

    @Override
    public TemporalQuery<ZonedDateTime> query() {
        return ZonedDateTime::from;
    }

    @Override
    public ZonedDateTime deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super ZonedDateTime> type)
            throws IOException {

        return ZonedDateTime.ofInstant(
                Instant.ofEpochMilli(decoder.decodeLong()),
                TemporalSerde.UTC
        );
    }
}
