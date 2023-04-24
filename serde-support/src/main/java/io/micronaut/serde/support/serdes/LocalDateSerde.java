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

import io.micronaut.serde.Encoder;
import io.micronaut.serde.config.SerdeConfiguration;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalQueries;
import java.time.temporal.TemporalQuery;

/**
 * Local date serde. Slightly different to {@link NumericSupportTemporalSerde}, we only support one
 * unit (epoch day)
 */
@Singleton
public final class LocalDateSerde extends DefaultFormattedTemporalSerde<LocalDate> implements TemporalSerde<LocalDate> {
    private final boolean writeNumeric;

    /**
     * Allows configuring a default time format for temporal date/time types.
     *
     * @param configuration The configuration
     */
    LocalDateSerde(SerdeConfiguration configuration) {
        super(configuration, DateTimeFormatter.ISO_LOCAL_DATE);
        this.writeNumeric = configuration.getTimeWriteShape() != SerdeConfiguration.TimeShape.STRING;
    }

    @Override
    public TemporalQuery<LocalDate> query() {
        return TemporalQueries.localDate();
    }

    @Override
    void serialize0(Encoder encoder, LocalDate value) throws IOException {
        if (writeNumeric) {
            encoder.encodeLong(value.toEpochDay());
        } else {
            super.serialize0(encoder, value);
        }
    }

    @Override
    LocalDate deserializeFallback(DateTimeException exc, String s) {
        long l;
        try {
            l = Long.parseLong(s);
        } catch (NumberFormatException e) {
            exc.addSuppressed(e);
            throw exc;
        }
        return LocalDate.ofEpochDay(l);
    }
}
