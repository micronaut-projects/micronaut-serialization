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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

final class StrictIJsonDateTimeFormat {
    static final ZoneId UTC = ZoneId.of("UTC");
    static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'xxx").withZone(UTC);

    private StrictIJsonDateTimeFormat() {
    }

    static String format(Instant value) {
        return format(value.atZone(UTC));
    }

    static String format(LocalDate value) {
        return format(value.atStartOfDay(UTC));
    }

    static String format(LocalDateTime value) {
        return format(value.atOffset(ZoneOffset.UTC));
    }

    static String format(ZonedDateTime value) {
        return FORMATTER.format(value);
    }

    static String format(OffsetDateTime value) {
        return FORMATTER.format(value);
    }

    static LocalDate parseLocalDate(String value) {
        return OffsetDateTime.parse(value, FORMATTER).toLocalDate();
    }

    static LocalDateTime parseLocalDateTime(String value) {
        return OffsetDateTime.parse(value, FORMATTER).toLocalDateTime();
    }

    static Instant parseInstant(String value) {
        return OffsetDateTime.parse(value, FORMATTER).toInstant();
    }
}
