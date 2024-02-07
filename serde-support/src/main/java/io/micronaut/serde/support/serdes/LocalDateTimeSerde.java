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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalQuery;

/**
 * Temporal serde for LocalDateTime.
 *
 * @since 1.0.0
 */
public final class LocalDateTimeSerde extends DefaultFormattedTemporalSerde<LocalDateTime>
        implements TemporalSerde<LocalDateTime>, SerdeRegistrar<LocalDateTime> {

    public LocalDateTimeSerde(SerdeConfiguration configuration) {
        super(configuration, DateTimeFormatter.ISO_DATE_TIME);
    }

    @Override
    public TemporalQuery<LocalDateTime> query() {
        return LocalDateTime::from;
    }

    @Override
    protected DefaultFormattedTemporalSerde<LocalDateTime> createSpecific(SerdeConfiguration configuration) {
        return new LocalDateTimeSerde(configuration);
    }

    @Override
    public Argument<LocalDateTime> getType() {
        return Argument.of(LocalDateTime.class);
    }
}
