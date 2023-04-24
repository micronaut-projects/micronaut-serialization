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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalQuery;

/**
 * Temporal serde for LocalDateTime.
 *
 * @since 1.0.0
 */
@Singleton
public final class LocalDateTimeSerde extends DefaultFormattedTemporalSerde<LocalDateTime>
        implements TemporalSerde<LocalDateTime> {

    LocalDateTimeSerde(SerdeConfiguration configuration) {
        super(configuration, DateTimeFormatter.ISO_DATE_TIME);
    }

    @Override
    public TemporalQuery<LocalDateTime> query() {
        return LocalDateTime::from;
    }
}
