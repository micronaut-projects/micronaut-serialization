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

import org.jspecify.annotations.Nullable;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Formats and parses the common shapes of the JDK ISO formatters without going through
 * {@link DateTimeFormatter}, which builds a parse context, a field map and a resolver per value.
 *
 * <p>Formatting produces exactly what the formatter produces, for years 0 to 9999. Parsing accepts
 * only the plain shape of the formatter: a four digit year, upper case {@code T} and {@code Z},
 * and values in range. For any other input, including input the formatter would reject, the
 * methods return {@code null} so that the caller falls back to the formatter, which then gives the
 * same result or the same error as before.</p>
 */
final class IsoTemporalFormat {

    private static final int NANOS_PER_MILLI = 1_000_000;
    private static final int NANOS_PER_MICRO = 1_000;
    private static final int MAX_YEAR = 9999;

    private IsoTemporalFormat() {
    }

    /**
     * Formats like {@link DateTimeFormatter#ISO_INSTANT}.
     *
     * @param value The value
     * @return The text, or {@code null} for a year outside 0 to 9999
     */
    static @Nullable String formatInstant(Instant value) {
        LocalDateTime dateTime = LocalDateTime.ofEpochSecond(value.getEpochSecond(), 0, ZoneOffset.UTC);
        if (!fastYear(dateTime.getYear())) {
            return null;
        }
        // yyyy-MM-ddTHH:mm:ss + up to 10 fraction chars + Z
        char[] buf = new char[30];
        int pos = writeDate(buf, 0, dateTime.getYear(), dateTime.getMonthValue(), dateTime.getDayOfMonth());
        buf[pos++] = 'T';
        pos = writeHourMinute(buf, pos, dateTime.getHour(), dateTime.getMinute());
        buf[pos++] = ':';
        pos = write2(buf, pos, dateTime.getSecond());
        int nano = value.getNano();
        if (nano != 0) {
            buf[pos++] = '.';
            if (nano % NANOS_PER_MILLI == 0) {
                pos = writeDigits(buf, pos, nano / NANOS_PER_MILLI, 3);
            } else if (nano % NANOS_PER_MICRO == 0) {
                pos = writeDigits(buf, pos, nano / NANOS_PER_MICRO, 6);
            } else {
                pos = writeDigits(buf, pos, nano, 9);
            }
        }
        buf[pos++] = 'Z';
        return new String(buf, 0, pos);
    }

    /**
     * Formats like {@link DateTimeFormatter#ISO_LOCAL_DATE}.
     *
     * @param value The value
     * @return The text, or {@code null} for a year outside 0 to 9999
     */
    static @Nullable String formatLocalDate(LocalDate value) {
        if (!fastYear(value.getYear())) {
            return null;
        }
        char[] buf = new char[10];
        writeDate(buf, 0, value.getYear(), value.getMonthValue(), value.getDayOfMonth());
        return new String(buf);
    }

    /**
     * Formats like {@link DateTimeFormatter#ISO_LOCAL_DATE_TIME}, which is also what
     * {@link DateTimeFormatter#ISO_DATE_TIME} writes for a {@link LocalDateTime}.
     *
     * @param value The value
     * @return The text, or {@code null} for a year outside 0 to 9999
     */
    static @Nullable String formatLocalDateTime(LocalDateTime value) {
        if (!fastYear(value.getYear())) {
            return null;
        }
        char[] buf = new char[29];
        int pos = writeLocalDateTime(buf, value);
        return new String(buf, 0, pos);
    }

    /**
     * Formats like {@link DateTimeFormatter#ISO_OFFSET_DATE_TIME}.
     *
     * @param value The value
     * @return The text, or {@code null} for a year outside 0 to 9999
     */
    static @Nullable String formatOffsetDateTime(OffsetDateTime value) {
        LocalDateTime dateTime = value.toLocalDateTime();
        if (!fastYear(dateTime.getYear())) {
            return null;
        }
        // local date time (29) + offset (9)
        char[] buf = new char[38];
        int pos = writeLocalDateTime(buf, dateTime);
        int totalSeconds = value.getOffset().getTotalSeconds();
        if (totalSeconds == 0) {
            buf[pos++] = 'Z';
        } else {
            int abs = Math.abs(totalSeconds);
            buf[pos++] = totalSeconds < 0 ? '-' : '+';
            pos = writeHourMinute(buf, pos, abs / 3600, (abs / 60) % 60);
            int seconds = abs % 60;
            if (seconds != 0) {
                buf[pos++] = ':';
                pos = write2(buf, pos, seconds);
            }
        }
        return new String(buf, 0, pos);
    }

    /**
     * Parses what {@link DateTimeFormatter#ISO_INSTANT} parses, for the shape {@code yyyy-MM-ddTHH:mm:ss[.f]Z}.
     *
     * @param text The text
     * @return The value, or {@code null} to fall back to the formatter
     */
    static @Nullable Instant parseInstant(String text) {
        int len = text.length();
        // the instant parser requires the seconds
        if (len < 20 || text.charAt(len - 1) != 'Z' || text.charAt(16) != ':') {
            return null;
        }
        LocalDateTime dateTime = parseLocalDateTime(text, len - 1);
        return dateTime == null ? null : dateTime.toInstant(ZoneOffset.UTC);
    }

    /**
     * Parses what {@link DateTimeFormatter#ISO_LOCAL_DATE} parses, for the shape {@code yyyy-MM-dd}.
     *
     * @param text The text
     * @return The value, or {@code null} to fall back to the formatter
     */
    static @Nullable LocalDate parseLocalDate(String text) {
        if (text.length() != 10) {
            return null;
        }
        return parseDate(text);
    }

    /**
     * Parses what {@link DateTimeFormatter#ISO_DATE_TIME} parses into a {@link LocalDateTime}, for the shape
     * {@code yyyy-MM-ddTHH:mm[:ss[.f]]} without an offset or zone.
     *
     * @param text The text
     * @return The value, or {@code null} to fall back to the formatter
     */
    static @Nullable LocalDateTime parseLocalDateTime(String text) {
        return parseLocalDateTime(text, text.length());
    }

    /**
     * Parses what {@link DateTimeFormatter#ISO_OFFSET_DATE_TIME} parses, for the shape
     * {@code yyyy-MM-ddTHH:mm[:ss[.f]]} followed by {@code Z}, {@code +HH:MM} or {@code +HH:MM:SS}.
     *
     * @param text The text
     * @return The value, or {@code null} to fall back to the formatter
     */
    static @Nullable OffsetDateTime parseOffsetDateTime(String text) {
        int len = text.length();
        if (len < 17) {
            return null;
        }
        int end;
        int offsetSeconds;
        if (text.charAt(len - 1) == 'Z') {
            end = len - 1;
            offsetSeconds = 0;
        } else {
            int signPos;
            int seconds = 0;
            if (len >= 25 && text.charAt(len - 3) == ':' && text.charAt(len - 6) == ':' && isSign(text.charAt(len - 9))) {
                signPos = len - 9;
                seconds = parse2(text, len - 2);
            } else if (text.charAt(len - 3) == ':' && isSign(text.charAt(len - 6))) {
                signPos = len - 6;
            } else {
                return null;
            }
            int hours = parse2(text, signPos + 1);
            int minutes = parse2(text, signPos + 4);
            if (hours < 0 || minutes < 0 || seconds < 0 || minutes > 59 || seconds > 59) {
                return null;
            }
            int total = hours * 3600 + minutes * 60 + seconds;
            offsetSeconds = text.charAt(signPos) == '-' ? -total : total;
            end = signPos;
        }
        LocalDateTime dateTime = parseLocalDateTime(text, end);
        if (dateTime == null) {
            return null;
        }
        try {
            return OffsetDateTime.of(dateTime, ZoneOffset.ofTotalSeconds(offsetSeconds));
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static @Nullable LocalDateTime parseLocalDateTime(String text, int end) {
        if (end < 16 || text.charAt(10) != 'T') {
            return null;
        }
        LocalDate date = parseDate(text);
        if (date == null) {
            return null;
        }
        LocalTime time = parseTime(text, 11, end);
        return time == null ? null : LocalDateTime.of(date, time);
    }

    private static @Nullable LocalDate parseDate(String text) {
        if (text.charAt(4) != '-' || text.charAt(7) != '-') {
            return null;
        }
        int year = parse4(text, 0);
        int month = parse2(text, 5);
        int day = parse2(text, 8);
        if (year < 0 || month < 1 || month > 12 || day < 1 || day > 31) {
            return null;
        }
        try {
            return LocalDate.of(year, month, day);
        } catch (DateTimeException e) {
            // such as February 30: the formatter reports it
            return null;
        }
    }

    private static @Nullable LocalTime parseTime(String text, int start, int end) {
        if (end - start < 5 || text.charAt(start + 2) != ':') {
            return null;
        }
        int hour = parse2(text, start);
        int minute = parse2(text, start + 3);
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return null;
        }
        int pos = start + 5;
        int second = 0;
        int nano = 0;
        if (pos < end) {
            if (end - pos < 3 || text.charAt(pos) != ':') {
                return null;
            }
            second = parse2(text, pos + 1);
            if (second < 0 || second > 59) {
                return null;
            }
            pos += 3;
            if (pos < end) {
                int digits = end - pos - 1;
                if (text.charAt(pos) != '.' || digits < 1 || digits > 9) {
                    return null;
                }
                for (int i = pos + 1; i < end; i++) {
                    int d = digit(text.charAt(i));
                    if (d < 0) {
                        return null;
                    }
                    nano = nano * 10 + d;
                }
                for (int i = digits; i < 9; i++) {
                    nano *= 10;
                }
            }
        }
        return LocalTime.of(hour, minute, second, nano);
    }

    private static boolean fastYear(int year) {
        return year >= 0 && year <= MAX_YEAR;
    }

    private static boolean isSign(char c) {
        return c == '+' || c == '-';
    }

    private static int digit(char c) {
        return c >= '0' && c <= '9' ? c - '0' : -1;
    }

    private static int parse2(String text, int pos) {
        int a = digit(text.charAt(pos));
        int b = digit(text.charAt(pos + 1));
        return a < 0 || b < 0 ? -1 : a * 10 + b;
    }

    private static int parse4(String text, int pos) {
        int a = parse2(text, pos);
        int b = parse2(text, pos + 2);
        return a < 0 || b < 0 ? -1 : a * 100 + b;
    }

    private static int writeLocalDateTime(char[] buf, LocalDateTime value) {
        int pos = writeDate(buf, 0, value.getYear(), value.getMonthValue(), value.getDayOfMonth());
        buf[pos++] = 'T';
        pos = writeHourMinute(buf, pos, value.getHour(), value.getMinute());
        // ISO_LOCAL_TIME always prints the seconds when formatting
        buf[pos++] = ':';
        pos = write2(buf, pos, value.getSecond());
        int nano = value.getNano();
        if (nano != 0) {
            // ISO_LOCAL_TIME prints the fraction without trailing zeros
            int digits = 9;
            while (nano % 10 == 0) {
                nano /= 10;
                digits--;
            }
            buf[pos++] = '.';
            pos = writeDigits(buf, pos, nano, digits);
        }
        return pos;
    }

    private static int writeDate(char[] buf, int pos, int year, int month, int day) {
        pos = writeDigits(buf, pos, year, 4);
        buf[pos++] = '-';
        pos = write2(buf, pos, month);
        buf[pos++] = '-';
        return write2(buf, pos, day);
    }

    private static int writeHourMinute(char[] buf, int pos, int hour, int minute) {
        pos = write2(buf, pos, hour);
        buf[pos++] = ':';
        return write2(buf, pos, minute);
    }

    private static int write2(char[] buf, int pos, int value) {
        buf[pos] = (char) ('0' + value / 10);
        buf[pos + 1] = (char) ('0' + value % 10);
        return pos + 2;
    }

    private static int writeDigits(char[] buf, int pos, int value, int width) {
        for (int i = pos + width - 1; i >= pos; i--) {
            buf[i] = (char) ('0' + value % 10);
            value /= 10;
        }
        return pos + width;
    }
}
