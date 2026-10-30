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
package io.micronaut.serde.oracle.jdbc.json;

import io.micronaut.core.annotation.Internal;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Period;

/**
 * Formats OSON dates, timestamps and intervals as strings in the same shape the {@code OracleJsonParser} path
 * produces, so that a value read into a {@code String}, {@code Map} or {@code Object} does not change when the
 * native decoder is used. The shapes were taken from the strings the public Oracle JSON API returns:
 * <ul>
 *     <li>dates and timestamps always carry seconds, and a fraction when it is not zero</li>
 *     <li>timestamps with a time zone always carry a fraction, and {@code Z} for UTC</li>
 *     <li>a fraction has six digits, or nine when it is not a whole number of microseconds</li>
 *     <li>day to second intervals have a days part, {@code P3DT4H5M6.700000S}, and {@code P0D} when zero</li>
 *     <li>year to month intervals are {@code P2Y11M}, and {@code P0Y} when zero</li>
 * </ul>
 * Negative intervals, which the parser path cannot read, are prefixed with {@code -} so that
 * {@link Duration#parse(CharSequence)} and {@link Period#parse(CharSequence)} accept them.
 *
 * @since 3.3.0
 */
@Internal
final class OracleOsonFormat {

    private static final int NANOS_PER_MICRO = 1000;

    private OracleOsonFormat() {
    }

    /**
     * @param value         the date or timestamp
     * @param alwaysFraction whether to print a zero fraction
     * @return the value as the parser path prints it
     */
    static String dateTime(LocalDateTime value, boolean alwaysFraction) {
        StringBuilder sb = new StringBuilder(32);
        sb.append(value.toLocalDate()).append('T');
        twoDigits(sb, value.getHour()).append(':');
        twoDigits(sb, value.getMinute()).append(':');
        twoDigits(sb, value.getSecond());
        fraction(sb, value.getNano(), alwaysFraction);
        return sb.toString();
    }

    /**
     * @param value the timestamp with time zone
     * @return the value as the parser path prints it
     */
    static String offsetDateTime(OffsetDateTime value) {
        return dateTime(value.toLocalDateTime(), true) + value.getOffset().getId();
    }

    /**
     * @param value the day to second interval
     * @return the value as the parser path prints it
     */
    static String duration(Duration value) {
        if (value.isZero()) {
            return "P0D";
        }
        Duration abs = value.abs();
        StringBuilder sb = new StringBuilder(24);
        if (value.isNegative()) {
            sb.append('-');
        }
        sb.append('P');
        long days = abs.toDays();
        if (days != 0) {
            sb.append(days).append('D');
        }
        int hours = abs.toHoursPart();
        int minutes = abs.toMinutesPart();
        int seconds = abs.toSecondsPart();
        int nanos = abs.toNanosPart();
        if (hours != 0 || minutes != 0 || seconds != 0 || nanos != 0) {
            sb.append('T');
            if (hours != 0) {
                sb.append(hours).append('H');
            }
            if (minutes != 0) {
                sb.append(minutes).append('M');
            }
            if (seconds != 0 || nanos != 0) {
                sb.append(seconds);
                fraction(sb, nanos, false);
                sb.append('S');
            }
        }
        return sb.toString();
    }

    /**
     * @param value the year to month interval
     * @return the value as the parser path prints it
     */
    static String period(Period value) {
        if (value.isZero()) {
            return "P0Y";
        }
        StringBuilder sb = new StringBuilder(12);
        if (value.isNegative()) {
            sb.append('-');
        }
        sb.append('P');
        int years = Math.abs(value.getYears());
        int months = Math.abs(value.getMonths());
        if (years != 0) {
            sb.append(years).append('Y');
        }
        if (months != 0) {
            sb.append(months).append('M');
        }
        return sb.toString();
    }

    private static StringBuilder twoDigits(StringBuilder sb, int value) {
        if (value < 10) {
            sb.append('0');
        }
        return sb.append(value);
    }

    private static void fraction(StringBuilder sb, int nanos, boolean always) {
        if (nanos == 0 && !always) {
            return;
        }
        sb.append('.');
        if (nanos % NANOS_PER_MICRO == 0) {
            pad(sb, nanos / NANOS_PER_MICRO, 6);
        } else {
            pad(sb, nanos, 9);
        }
    }

    private static void pad(StringBuilder sb, int value, int width) {
        String digits = Integer.toString(value);
        for (int i = digits.length(); i < width; i++) {
            sb.append('0');
        }
        sb.append(digits);
    }
}
