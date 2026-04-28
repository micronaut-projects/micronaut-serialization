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
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalQuery;
import java.util.Set;

/**
 * Zoned date time serde.
 *
 * @since 1.0.0
 */
public final class ZonedDateTimeSerde
    extends NumericSupportTemporalSerde<ZonedDateTime>
        implements TemporalSerde<ZonedDateTime>, SerdeRegistrar<ZonedDateTime> {
    private final ZoneId adjustTimeZone;

    public ZonedDateTimeSerde(SerdeConfiguration configuration) {
        this(
            stringFormatter(configuration, DateTimeFormatter.ISO_ZONED_DATE_TIME),
            configuration.getTimeWriteShape(),
            configuration.getNumericTimeUnit(),
            null
        );
    }

    private ZonedDateTimeSerde(DateTimeFormatter stringFormatter,
                               SerdeConfiguration.TimeShape writeShape,
                               SerdeConfiguration.NumericTimeUnit numericUnit,
                               ZoneId adjustTimeZone) {
        super(
            stringFormatter,
            SerdeConfiguration.NumericTimeUnit.MILLISECONDS,
            writeShape,
            numericUnit
        );
        this.adjustTimeZone = adjustTimeZone;
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
    ZonedDateTime parseString(String text) {
        ZonedDateTime value = super.parseString(text);
        return adjustTimeZone == null ? value : value.withZoneSameInstant(adjustTimeZone);
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
    protected DefaultFormattedTemporalSerde<ZonedDateTime> createSpecific(DateTimeFormatter stringFormatter,
                                                                         SerdeConfiguration.TimeShape timeWriteShape,
                                                                         SerdeConfiguration.NumericTimeUnit numericUnit) {
        return new ZonedDateTimeSerde(stringFormatter, timeWriteShape, numericUnit, null);
    }

    @Override
    protected DefaultFormattedTemporalSerde<ZonedDateTime> createSpecific(DateTimeFormatter stringFormatter,
                                                                         SerdeConfiguration.TimeShape timeWriteShape,
                                                                         SerdeConfiguration.NumericTimeUnit numericUnit,
                                                                         @NonNull FormatConfiguration format) {
        return new ZonedDateTimeSerde(
            stringFormatter,
            timeWriteShape,
            numericUnit,
            null
        );
    }

    @Override
    protected DefaultFormattedTemporalSerde<ZonedDateTime> createSpecificForDeserialization(@NonNull DateTimeFormatter stringFormatter,
                                                                                            SerdeConfiguration.TimeShape timeWriteShape,
                                                                                            SerdeConfiguration.NumericTimeUnit numericUnit,
                                                                                            @NonNull FormatConfiguration format,
                                                                                            @NonNull Set<DeserializationConfiguration.Feature> features) {
        return new ZonedDateTimeSerde(
            stringFormatter,
            timeWriteShape,
            numericUnit,
            adjustTimeZone(format, features)
        );
    }

    @Override
    protected DefaultFormattedTemporalSerde<ZonedDateTime> createSpecificForDeserialization(@NonNull DateTimeFormatter stringFormatter,
                                                                                            SerdeConfiguration.TimeShape timeWriteShape,
                                                                                            SerdeConfiguration.NumericTimeUnit numericUnit,
                                                                                            @NonNull SerdeConfiguration configuration,
                                                                                            @NonNull Set<DeserializationConfiguration.Feature> features) {
        return new ZonedDateTimeSerde(
            stringFormatter,
            timeWriteShape,
            numericUnit,
            adjustTimeZone(configuration, features)
        );
    }

    @Override
    public Argument<ZonedDateTime> getType() {
        return Argument.of(ZonedDateTime.class);
    }

    @Override
    protected DateTimeFormatter defaultStringFormatter(@NonNull FormatConfiguration format,
                                                       @NonNull Set<SerdeConfiguration.Feature> features) {
        if (!features.contains(SerdeConfiguration.Feature.WRITE_DATES_WITH_ZONE_ID)) {
            return DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        }
        return DateTimeFormatter.ISO_ZONED_DATE_TIME;
    }

    private static ZoneId adjustTimeZone(@NonNull FormatConfiguration format,
                                         @NonNull Set<DeserializationConfiguration.Feature> features) {
        if (features.contains(DeserializationConfiguration.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)) {
            return format.parseTimeZone().toZoneId();
        }
        return null;
    }

    private static ZoneId adjustTimeZone(@NonNull SerdeConfiguration configuration,
                                         @NonNull Set<DeserializationConfiguration.Feature> features) {
        if (features.contains(DeserializationConfiguration.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)) {
            return configuration.getTimeZone()
                .map(timeZone -> timeZone.toZoneId())
                .orElse(TemporalSerde.UTC);
        }
        return null;
    }

    private static DateTimeFormatter stringFormatter(SerdeConfiguration configuration, DateTimeFormatter defaultStringFormatter) {
        return createFormatter(configuration).orElse(defaultStringFormatter);
    }
}
