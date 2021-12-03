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
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalQueries;
import java.time.temporal.TemporalQuery;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.config.SerdeConfiguration;
import jakarta.inject.Singleton;

/**
 * LocalTime serde.
 *
 * @since 1.0.0
 */
@Singleton
public class LocalTimeSerde extends DefaultFormattedTemporalSerde<LocalTime> {
    /**
     * Allows configuring a default time format for temporal date/time types.
     *
     * @param configuration The configuration
     */
    protected LocalTimeSerde(SerdeConfiguration configuration) {
        super(configuration);
    }

    @Override
    protected DateTimeFormatter getDefaultFormatter() {
        return DateTimeFormatter.ISO_LOCAL_TIME;
    }

    @Override
    public TemporalQuery<LocalTime> query() {
        return TemporalQueries.localTime();
    }

    @Override
    protected void serializeWithoutFormat(Encoder encoder, EncoderContext context, LocalTime value, Argument<? extends LocalTime> type) throws IOException {
        encoder.encodeLong(value.toNanoOfDay());
    }

    @Override
    protected LocalTime deserializeNonNullWithoutFormat(Decoder decoder, DecoderContext decoderContext, Argument<? super LocalTime> type) throws IOException {
        return LocalTime.ofNanoOfDay(decoder.decodeLong());
    }
}
