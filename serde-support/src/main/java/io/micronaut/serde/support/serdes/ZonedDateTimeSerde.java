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

import io.micronaut.core.type.Argument;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.support.SerdeRegistrar;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalQuery;

/**
 * Zoned date time serde.
 *
 * @since 1.0.0
 */
public final class ZonedDateTimeSerde
    extends NumericSupportTemporalSerde<ZonedDateTime>
        implements TemporalSerde<ZonedDateTime>, SerdeRegistrar<ZonedDateTime> {

    ZonedDateTimeSerde(SerdeConfiguration configuration) {
        super(configuration, DateTimeFormatter.ISO_ZONED_DATE_TIME, SerdeConfiguration.NumericTimeUnit.MILLISECONDS);
    }

    @Override
    public TemporalQuery<ZonedDateTime> query() {
        return ZonedDateTime::from;
    }

    @Override
    protected ZonedDateTime fromNanos(long seconds, int nanos) {
        return ZonedDateTime.ofInstant(Instant.ofEpochSecond(seconds, nanos), UTC);
    }

    @Override
    protected long getSecondPart(ZonedDateTime value) {
        return value.toInstant().getEpochSecond();
    }

    @Override
    protected int getNanoPart(ZonedDateTime value) {
        return value.toInstant().getNano();
    }

    @Override
    protected DefaultFormattedTemporalSerde<ZonedDateTime> createSpecific(SerdeConfiguration configuration) {
        return new ZonedDateTimeSerde(configuration);
    }

    @Override
    public Argument<ZonedDateTime> getType() {
        return Argument.of(ZonedDateTime.class);
    }
}
