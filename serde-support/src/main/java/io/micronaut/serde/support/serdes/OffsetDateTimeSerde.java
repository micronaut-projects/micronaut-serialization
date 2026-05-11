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
import io.micronaut.serde.FormatConfiguration;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.support.SerdeRegistrar;
import org.jspecify.annotations.Nullable;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalQueries;
import java.time.temporal.TemporalQuery;
import java.util.Set;

/**
 * Serde for OffsetDateTime.
 */
public final class OffsetDateTimeSerde extends NumericSupportTemporalSerde<OffsetDateTime> implements SerdeRegistrar<OffsetDateTime> {
    private static final TemporalQuery<OffsetDateTime> QUERY = temporal -> {
        try {
            return OffsetDateTime.from(temporal);
        } catch (DateTimeException e) {
            ZoneId zone = temporal.query(TemporalQueries.zone());
            if (zone != null) {
                try {
                    return OffsetDateTime.ofInstant(Instant.from(temporal), zone);
                } catch (DateTimeException fallbackException) {
                    e.addSuppressed(fallbackException);
                }
            }
            throw e;
        }
    };

    @Nullable
    private final ZoneId adjustTimeZone;

    /**
     * Allows configuring a default time format for temporal date/time types.
     *
     * @param configuration The configuration
     */
    public OffsetDateTimeSerde(SerdeConfiguration configuration) {
        this(
            stringFormatter(configuration),
            configuration.getTimeWriteShape(),
            configuration.getNumericTimeUnit(),
            null
        );
    }

    private OffsetDateTimeSerde(DateTimeFormatter stringFormatter,
                                SerdeConfiguration.TimeShape writeShape,
                                SerdeConfiguration.NumericTimeUnit numericUnit,
                                @Nullable ZoneId adjustTimeZone) {
        super(
            stringFormatter,
            SerdeConfiguration.NumericTimeUnit.MILLISECONDS,
            writeShape,
            numericUnit
        );
        this.adjustTimeZone = adjustTimeZone;
    }

    @Override
    public TemporalQuery<OffsetDateTime> query() {
        return QUERY;
    }

    @Override
    protected OffsetDateTime fromNanos(long seconds, int nanos) {
        return OffsetDateTime.ofInstant(
            Instant.ofEpochSecond(seconds, nanos),
            TemporalSerde.UTC
        );
    }

    @Override
    OffsetDateTime parseString(String text) {
        OffsetDateTime value = super.parseString(text);
        return adjustTimeZone == null ? value : value.atZoneSameInstant(adjustTimeZone).toOffsetDateTime();
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
    protected DefaultFormattedTemporalSerde<OffsetDateTime> createSpecific(DateTimeFormatter stringFormatter,
                                                                          SerdeConfiguration.TimeShape timeWriteShape,
                                                                          SerdeConfiguration.NumericTimeUnit numericUnit) {
        return new OffsetDateTimeSerde(stringFormatter, timeWriteShape, numericUnit, null);
    }

    @Override
    protected DefaultFormattedTemporalSerde<OffsetDateTime> createSpecific(DateTimeFormatter stringFormatter,
                                                                          SerdeConfiguration.TimeShape timeWriteShape,
                                                                          SerdeConfiguration.NumericTimeUnit numericUnit,
                                                                          FormatConfiguration format) {
        return new OffsetDateTimeSerde(stringFormatter, timeWriteShape, numericUnit, null);
    }

    @Override
    protected DefaultFormattedTemporalSerde<OffsetDateTime> createSpecificForDeserialization(DateTimeFormatter stringFormatter,
                                                                                             SerdeConfiguration.TimeShape timeWriteShape,
                                                                                             SerdeConfiguration.NumericTimeUnit numericUnit,
                                                                                             FormatConfiguration format,
                                                                                             Set<DeserializationConfiguration.Feature> features) {
        return new OffsetDateTimeSerde(stringFormatter, timeWriteShape, numericUnit, adjustTimeZone(format, features));
    }

    @Override
    protected DefaultFormattedTemporalSerde<OffsetDateTime> createSpecificForDeserialization(DateTimeFormatter stringFormatter,
                                                                                             SerdeConfiguration.TimeShape timeWriteShape,
                                                                                             SerdeConfiguration.NumericTimeUnit numericUnit,
                                                                                             SerdeConfiguration configuration,
                                                                                             Set<DeserializationConfiguration.Feature> features) {
        return new OffsetDateTimeSerde(stringFormatter, timeWriteShape, numericUnit, adjustTimeZone(configuration, features));
    }

    @Override
    public Argument<OffsetDateTime> getType() {
        return Argument.of(OffsetDateTime.class);
    }

    @Nullable
    private static ZoneId adjustTimeZone(FormatConfiguration format,
                                         Set<DeserializationConfiguration.Feature> features) {
        if (features.contains(DeserializationConfiguration.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)) {
            return format.parseTimeZone().toZoneId();
        }
        return null;
    }

    @Nullable
    private static ZoneId adjustTimeZone(SerdeConfiguration configuration,
                                         Set<DeserializationConfiguration.Feature> features) {
        if (features.contains(DeserializationConfiguration.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)) {
            return configuration.getTimeZone()
                .map(timeZone -> timeZone.toZoneId())
                .orElse(TemporalSerde.UTC);
        }
        return null;
    }

    private static DateTimeFormatter stringFormatter(SerdeConfiguration configuration) {
        return createFormatter(configuration).orElse(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
