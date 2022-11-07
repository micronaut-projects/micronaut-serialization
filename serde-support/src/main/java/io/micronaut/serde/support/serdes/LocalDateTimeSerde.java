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
package io.micronaut.serde.support.serdes;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalQuery;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.config.SerdeConfiguration;
import jakarta.inject.Singleton;

/**
 * Temporal serde for LocalDateTime.
 *
 * @since 1.0.0
 */
@Singleton
public class LocalDateTimeSerde extends DefaultFormattedTemporalSerde<LocalDateTime>
        implements TemporalSerde<LocalDateTime> {

    protected LocalDateTimeSerde(SerdeConfiguration configuration) {
        super(configuration);
    }

    @Override
    protected DateTimeFormatter getDefaultFormatter() {
        return DateTimeFormatter.ISO_DATE_TIME;
    }

    @Override
    public TemporalQuery<LocalDateTime> query() {
        return LocalDateTime::from;
    }

    @Override
    protected void serializeWithoutFormat(Encoder encoder, EncoderContext context, LocalDateTime value, Argument<? extends LocalDateTime> type) throws IOException {
        encoder.encodeString(getDefaultFormatter().format(value));
    }

    @Override
    protected LocalDateTime deserializeNonNullWithoutFormat(Decoder decoder, DecoderContext decoderContext, Argument<? super LocalDateTime> type) throws IOException {
        return getDefaultFormatter().parse(decoder.decodeString(), LocalDateTime::from);
    }
}
