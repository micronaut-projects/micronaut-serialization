/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.FormatConfiguration;
import io.micronaut.serde.FormattedSerde;
import io.micronaut.serde.Serde;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.SerdeRegistrar;
import io.micronaut.serde.support.util.SerdeFeatures;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Optional;
import java.util.TimeZone;

/**
 * Serde mapping for calendar values.
 */
@Internal
final class CalendarSerde implements FormattedSerde<Calendar>, SerdeRegistrar<Calendar> {
    private static final Argument<Calendar> ARGUMENT = Argument.of(Calendar.class);
    private static final DateTimeFormatter STRICT_IJSON_FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'xxx");
    private final boolean strictIJson;
    private final SerdeConfiguration.TimeShape writeShape;
    private final Optional<SimpleDateFormat> configuredDateFormat;

    /**
     * @param serdeConfiguration The active Serde configuration used to select
     *                           strict I-JSON date/time formatting
     */
    CalendarSerde(SerdeConfiguration serdeConfiguration) {
        this.strictIJson = serdeConfiguration.isWriteDateTimesAsStrictIJson();
        this.writeShape = serdeConfiguration.getTimeWriteShape();
        this.configuredDateFormat = configuredDateFormat(serdeConfiguration);
    }

    @Override
    public Serializer<Calendar> createSpecific(EncoderContext context, Argument<? extends Calendar> type) {
        context = SerdeFeatures.withFeatures(context, type.getAnnotationMetadata());
        FormatConfiguration format = FormatConfiguration.from(type.getAnnotationMetadata());
        return format == null ? createSpecificWithoutFormat() : createSpecific(context, type, format);
    }

    @Override
    public Serializer<Calendar> createSpecific(EncoderContext context,
                                               Argument<? extends Calendar> type,
                                               FormatConfiguration format) {
        if (format.shape().isNumeric()) {
            return new TimestampMillisCalendarSerde();
        }
        Optional<SimpleDateFormat> dateFormat = format.createDateFormat();
        if (dateFormat.isPresent()) {
            return new FormattedCalendarSerde(dateFormat.get());
        }
        return this;
    }

    @Override
    public Deserializer<Calendar> createSpecific(DecoderContext context, Argument<? super Calendar> type)
        throws SerdeException {
        context = SerdeFeatures.withFeatures(context, type.getAnnotationMetadata());
        FormatConfiguration format = FormatConfiguration.from(type.getAnnotationMetadata());
        return format == null ? createSpecificWithoutFormat() : createSpecific(context, type, format);
    }

    @Override
    public Deserializer<Calendar> createSpecific(DecoderContext context,
                                                 Argument<? super Calendar> type,
                                                 FormatConfiguration format) throws SerdeException {
        if (format.shape().isNumeric()) {
            return new TimestampMillisCalendarSerde();
        }
        Optional<SimpleDateFormat> dateFormat = format.createDateFormat();
        if (dateFormat.isPresent()) {
            return new FormattedCalendarSerde(dateFormat.get());
        }
        return this;
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Calendar> type, Calendar value) throws IOException {
        encoder.encodeString(format(value, strictIJson));
    }

    @Override
    public Calendar deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Calendar> type) throws IOException {
        return parse(decoder.decodeString());
    }

    /**
     * Formats a JSON-B calendar value using the same rules as generated JSON-B
     * scalar metadata.
     *
     * @param calendar    The calendar to format
     * @param strictIJson Whether strict I-JSON date/time output is enabled
     * @return The JSON-B calendar string
     */
    private String format(Calendar calendar, boolean strictIJson) {
        ZoneId zone = calendar.getTimeZone().toZoneId();
        ZonedDateTime zonedDateTime = calendar.toInstant().atZone(zone);
        if (strictIJson) {
            return STRICT_IJSON_FORMATTER.format(zonedDateTime);
        }
        if (hasTime(calendar)) {
            return DateTimeFormatter.ISO_DATE_TIME.format(zonedDateTime);
        }
        return DateTimeFormatter.ISO_DATE.format(zonedDateTime);
    }

    /**
     * Parses a JSON-B calendar string.
     *
     * @param value The encoded calendar value
     * @return The parsed calendar
     */
    private Calendar parse(String value) {
        Calendar calendar = Calendar.getInstance();
        if (value.indexOf('T') >= 0) {
            ZonedDateTime zonedDateTime = ZonedDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME);
            calendar.setTimeZone(TimeZone.getTimeZone(zonedDateTime.getZone()));
            calendar.setTimeInMillis(zonedDateTime.toInstant().toEpochMilli());
            return calendar;
        }
        LocalDate date = LocalDate.parse(value.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE);
        ZoneOffset offset = value.length() == 10 ? ZoneOffset.UTC : ZoneOffset.of(value.substring(10));
        calendar.clear();
        calendar.setTimeZone(TimeZone.getTimeZone(offset));
        calendar.set(date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth());
        return calendar;
    }

    private static boolean hasTime(Calendar calendar) {
        return calendar.isSet(Calendar.HOUR)
            || calendar.isSet(Calendar.HOUR_OF_DAY)
            || calendar.isSet(Calendar.MINUTE)
            || calendar.isSet(Calendar.SECOND)
            || calendar.isSet(Calendar.MILLISECOND);
    }

    @Override
    public Argument<Calendar> getType() {
        return ARGUMENT;
    }

    private Serde<Calendar> createSpecificWithoutFormat() {
        if (configuredDateFormat.isPresent()) {
            return new FormattedCalendarSerde(configuredDateFormat.get());
        }
        if (writeShape != SerdeConfiguration.TimeShape.STRING) {
            return new TimestampMillisCalendarSerde();
        }
        return this;
    }

    private static Optional<SimpleDateFormat> configuredDateFormat(SerdeConfiguration configuration) {
        return configuration.getDateFormat().map(pattern -> {
            SimpleDateFormat dateFormat = configuration.getLocale()
                .map(locale -> new SimpleDateFormat(pattern, locale))
                .orElseGet(() -> new SimpleDateFormat(pattern));
            configuration.getTimeZone().ifPresent(dateFormat::setTimeZone);
            return dateFormat;
        });
    }

    private static final class TimestampMillisCalendarSerde implements Serde<Calendar> {
        @Override
        public void serialize(Encoder encoder,
                              EncoderContext context,
                              Argument<? extends Calendar> type,
                              Calendar value) throws IOException {
            encoder.encodeLong(value.getTimeInMillis());
        }

        @Override
        public Calendar deserialize(Decoder decoder,
                                    DecoderContext decoderContext,
                                    Argument<? super Calendar> type) throws IOException {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(decoder.decodeLong());
            return calendar;
        }
    }

    private static final class FormattedCalendarSerde implements Serde<Calendar> {
        private final SimpleDateFormat dateFormat;
        private final String pattern;

        private FormattedCalendarSerde(SimpleDateFormat dateFormat) {
            this.dateFormat = dateFormat;
            this.pattern = dateFormat.toPattern();
        }

        @Override
        public void serialize(Encoder encoder,
                              EncoderContext context,
                              Argument<? extends Calendar> type,
                              Calendar value) throws IOException {
            synchronized (dateFormat) {
                encoder.encodeString(dateFormat.format(value.getTime()));
            }
        }

        @Override
        public Calendar deserialize(Decoder decoder,
                                    DecoderContext decoderContext,
                                    Argument<? super Calendar> type) throws IOException {
            String value = decoder.decodeString();
            try {
                Calendar calendar = Calendar.getInstance(dateFormat.getTimeZone());
                synchronized (dateFormat) {
                    calendar.setTime(dateFormat.parse(value));
                }
                return calendar;
            } catch (ParseException e) {
                throw new SerdeException("Error decoding calendar of type " + type + " using pattern " + pattern + ": " + e.getMessage(), e);
            }
        }
    }
}
