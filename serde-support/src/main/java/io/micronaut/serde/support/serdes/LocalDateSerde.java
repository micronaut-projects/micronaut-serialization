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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalQueries;
import java.time.temporal.TemporalQuery;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.config.SerdeConfiguration;
import jakarta.inject.Singleton;

/**
 * Local date serde.
 */
@Singleton
public class LocalDateSerde extends DefaultFormattedTemporalSerde<LocalDate> implements TemporalSerde<LocalDate> {
    /**
     * Allows configuring a default time format for temporal date/time types.
     *
     * @param configuration The configuration
     */
    protected LocalDateSerde(SerdeConfiguration configuration) {
        super(configuration);
    }

    @Override
    protected DateTimeFormatter getDefaultFormatter() {
        return DateTimeFormatter.ISO_LOCAL_DATE;
    }

    @Override
    public TemporalQuery<LocalDate> query() {
        return TemporalQueries.localDate();
    }

    @Override
    protected void serializeWithoutFormat(Encoder encoder, EncoderContext context, LocalDate value, Argument<? extends LocalDate> type) throws IOException {
        encoder.encodeLong(value.toEpochDay());
    }

    @Override
    protected LocalDate deserializeNonNullWithoutFormat(Decoder decoder, DecoderContext decoderContext, Argument<? super LocalDate> type) throws IOException {
        return LocalDate.ofEpochDay(decoder.decodeLong());
    }
}
