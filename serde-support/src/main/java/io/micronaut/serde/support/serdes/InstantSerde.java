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
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalQuery;

/**
 * Serde used for {@link java.time.Instant}.
 *
 * @since 1.0.0
 */
@Singleton
public final class InstantSerde extends NumericSupportTemporalSerde<Instant> implements TemporalSerde<Instant> {

    private static final TemporalQuery<Instant> QUERY = Instant::from;

    InstantSerde(SerdeConfiguration configuration) {
        super(configuration, DateTimeFormatter.ISO_INSTANT, SerdeConfiguration.NumericTimeUnit.MILLISECONDS);
    }

    @Override
    public TemporalQuery<Instant> query() {
        return QUERY;
    }

    @Override
    protected long getSecondPart(Instant value) {
        return value.getEpochSecond();
    }

    @Override
    protected int getNanoPart(Instant value) {
        return value.getNano();
    }

    @Override
    protected Instant fromNanos(long seconds, int nanos) {
        return Instant.ofEpochSecond(seconds, nanos);
    }

    @Override
    protected DefaultFormattedTemporalSerde<Instant> createSpecific(SerdeConfiguration configuration) {
        return new InstantSerde(configuration);
    }
}
