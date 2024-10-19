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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.config.annotation.SerdeConfig;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Year;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQuery;
import java.util.Locale;

@Internal
final class FormattedTemporalSerde<T extends TemporalAccessor> implements TemporalSerde<T> {
    final DateTimeFormatter formatter;
    final TemporalQuery<T> query;
    final TemporalSerde<T> originalTemporalSerde;

    FormattedTemporalSerde(@NonNull String pattern,
                           @NonNull AnnotationMetadata annotationMetadata,
                           TemporalQuery<T> query,
                           TemporalSerde<T> originalTemporalSerde) {

        Locale locale = annotationMetadata.stringValue(SerdeConfig.class, SerdeConfig.LOCALE)
                .map(StringUtils::parseLocale)
                .orElse(null);
        DateTimeFormatter f = locale != null ? DateTimeFormatter.ofPattern(pattern, locale) :
                DateTimeFormatter.ofPattern(pattern);

        final ZoneId zone = annotationMetadata
                .stringValue(SerdeConfig.class, SerdeConfig.TIMEZONE)
                .map(ZoneId::of).orElse(UTC);

        this.formatter = f.withZone(zone);
        this.query = query;
        this.originalTemporalSerde = originalTemporalSerde;

    }

    FormattedTemporalSerde(DateTimeFormatter formatter,
                           TemporalQuery<T> query,
                           TemporalSerde<T> originalTemporalSerde) {
        this.formatter = formatter;
        this.query = query;
        this.originalTemporalSerde = originalTemporalSerde;
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException {
        encoder.encodeString(
                formatter.format(value)
        );
    }

    @Override
    public T deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super T> type) throws IOException {
        final String str = decoder.decodeString();
        try {
            return formatter.parse(str, query());
        } catch (DateTimeException e) {
            if (originalTemporalSerde instanceof DefaultFormattedTemporalSerde<T> defaultFormattedTemporalSerde) {
                return defaultFormattedTemporalSerde.deserializeFallback(e, str);
            } else {
                throw e;
            }
            //return deserializeFallback(e, str, type);
        }
    }

    @Override
    public TemporalQuery<T> query() {
        return query;
    }

    private T deserializeFallback(DateTimeException exc, String str, Argument<? super T> type) {
        BigDecimal raw;
        try {
            raw = new BigDecimal(str);
        } catch (NumberFormatException e) {
            exc.addSuppressed(e);
            throw exc;
        }

        String formattedStr = switch (type.getTypeName()) {
            case "java.time.Instant",
                 "java.time.ZonedDateTime",
                 "java.time.OffsetDateTime"
                 -> Instant.ofEpochMilli(raw.longValue()).atZone(formatter.getZone()).format(formatter);
            case "java.time.LocalDate" -> LocalDate.ofEpochDay(raw.longValue()).format(formatter);
            case "java.time.LocalTime" -> convertLocalTime(raw.longValue());
            case "java.time.LocalDateTime"
                 -> LocalDateTime.ofInstant(Instant.ofEpochMilli(raw.longValue()), formatter.getZone()).format(formatter);
            case "java.time.Year" -> Year.of(Integer.parseInt(str)).format(formatter);
            default -> throw new IllegalStateException("The type: " + type.getTypeName() + " with value: " + str);
        };

        return formatter.parse(formattedStr, query());
    }

    private String convertLocalTime(Long value) {
        try {
            return LocalTime.ofSecondOfDay(value).format(formatter);
        } catch (DateTimeException exc) {
            // The value was not deserialized as second of day.
            // We will ignore this error and try to deserialize it as a nano of day.
        }
        return LocalTime.ofNanoOfDay(value).format(formatter);
    }
}
