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

import io.micronaut.serde.config.SerdeConfiguration;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalQuery;

/**
 * Serde for OffsetDateTime.
 */
@Singleton
public final class OffsetDateTimeSerde extends NumericSupportTemporalSerde<OffsetDateTime> {
    /**
     * Allows configuring a default time format for temporal date/time types.
     *
     * @param configuration The configuration
     */
    OffsetDateTimeSerde(SerdeConfiguration configuration) {
        super(
            configuration,
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            SerdeConfiguration.NumericTimeUnit.MILLISECONDS
        );
    }

    @Override
    public TemporalQuery<OffsetDateTime> query() {
        return OffsetDateTime::from;
    }

    @Override
    protected OffsetDateTime fromNanos(long seconds, int nanos) {
        return OffsetDateTime.ofInstant(
            Instant.ofEpochSecond(seconds, nanos),
            TemporalSerde.UTC
        );
    }

    @Override
    protected long getSecondPart(OffsetDateTime value) {
        return value.toInstant().getEpochSecond();
    }

    @Override
    protected int getNanoPart(OffsetDateTime value) {
        return value.toInstant().getNano();
    }

    @Override
    protected DefaultFormattedTemporalSerde<OffsetDateTime> createSpecific(SerdeConfiguration configuration) {
        return new OffsetDateTimeSerde(configuration);
    }
}
