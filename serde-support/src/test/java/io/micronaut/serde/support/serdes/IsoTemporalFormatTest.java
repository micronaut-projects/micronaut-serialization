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

import io.micronaut.serde.config.SerdeConfiguration;
import org.junit.jupiter.api.Test;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalQuery;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the ISO fast paths against the JDK formatters they replace.
 */
class IsoTemporalFormatTest {

    private static final int[] NANOS = {0, 1, 10, 100_000_000, 120_000_000, 123_000_000, 123_400_000, 123_456_000, 123_456_700, 123_456_789, 999_999_999, 1_000, 5_000_000};
    private static final String MUTATIONS = "0123456789-:T.Z+tz W";

    @Test
    void formatsLikeTheFormatters() {
        Random random = new Random(42);
        for (int i = 0; i < 20_000; i++) {
            LocalDateTime dateTime = randomDateTime(random, 0, 9999);
            Instant instant = dateTime.toInstant(ZoneOffset.UTC);
            OffsetDateTime offsetDateTime = OffsetDateTime.of(dateTime, randomOffset(random));
            assertEquals(DateTimeFormatter.ISO_INSTANT.format(instant), IsoTemporalFormat.formatInstant(instant));
            assertEquals(DateTimeFormatter.ISO_LOCAL_DATE.format(dateTime.toLocalDate()), IsoTemporalFormat.formatLocalDate(dateTime.toLocalDate()));
            assertEquals(DateTimeFormatter.ISO_DATE_TIME.format(dateTime), IsoTemporalFormat.formatLocalDateTime(dateTime));
            assertEquals(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(offsetDateTime), IsoTemporalFormat.formatOffsetDateTime(offsetDateTime));
        }
    }

    @Test
    void leavesYearsOutsideFourDigitsToTheFormatter() {
        LocalDateTime big = LocalDateTime.of(10_000, 1, 1, 0, 0);
        LocalDateTime negative = LocalDateTime.of(-1, 1, 1, 0, 0);
        for (LocalDateTime dateTime : List.of(big, negative)) {
            assertNull(IsoTemporalFormat.formatInstant(dateTime.toInstant(ZoneOffset.UTC)));
            assertNull(IsoTemporalFormat.formatLocalDate(dateTime.toLocalDate()));
            assertNull(IsoTemporalFormat.formatLocalDateTime(dateTime));
            assertNull(IsoTemporalFormat.formatOffsetDateTime(dateTime.atOffset(ZoneOffset.UTC)));
        }
        assertEquals("0000-01-01T00:00:00Z", IsoTemporalFormat.formatInstant(LocalDateTime.of(0, 1, 1, 0, 0).toInstant(ZoneOffset.UTC)));
    }

    @Test
    void parsesWhatTheFormattersWrite() {
        Random random = new Random(7);
        for (int i = 0; i < 20_000; i++) {
            LocalDateTime dateTime = randomDateTime(random, 0, 9999);
            Instant instant = dateTime.toInstant(ZoneOffset.UTC);
            OffsetDateTime offsetDateTime = OffsetDateTime.of(dateTime, randomOffset(random));
            assertEquals(instant, IsoTemporalFormat.parseInstant(DateTimeFormatter.ISO_INSTANT.format(instant)));
            assertEquals(dateTime.toLocalDate(), IsoTemporalFormat.parseLocalDate(DateTimeFormatter.ISO_LOCAL_DATE.format(dateTime)));
            assertEquals(dateTime, IsoTemporalFormat.parseLocalDateTime(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(dateTime)));
            assertEquals(offsetDateTime, IsoTemporalFormat.parseOffsetDateTime(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(offsetDateTime)));
        }
    }

    @Test
    void parsesLocalDateTimeShapes() {
        List.of(
            "2026-11-20T11:38",
            "2026-11-20T11:38:06",
            "2026-11-20T11:38:06.1",
            "2026-11-20T11:38:06.12",
            "2026-11-20T11:38:06.123456789",
            "2026-11-20T00:00:00.000",
            "0000-01-01T00:00",
            "2024-02-29T23:59:59.999999999"
        ).forEach(IsoTemporalFormatTest::parsesLocalDateTimeShape);
    }

    private static void parsesLocalDateTimeShape(String text) {
        assertEquals(LocalDateTime.parse(text, DateTimeFormatter.ISO_DATE_TIME), IsoTemporalFormat.parseLocalDateTime(text));
        assertEquals(OffsetDateTime.parse(text + "Z", DateTimeFormatter.ISO_OFFSET_DATE_TIME), IsoTemporalFormat.parseOffsetDateTime(text + "Z"));
        assertEquals(OffsetDateTime.parse(text + "-05:30", DateTimeFormatter.ISO_OFFSET_DATE_TIME), IsoTemporalFormat.parseOffsetDateTime(text + "-05:30"));
        assertEquals(OffsetDateTime.parse(text + "+01:00:30", DateTimeFormatter.ISO_OFFSET_DATE_TIME), IsoTemporalFormat.parseOffsetDateTime(text + "+01:00:30"));
        assertEquals(OffsetDateTime.parse(text + "-00:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME), IsoTemporalFormat.parseOffsetDateTime(text + "-00:00"));
    }

    @Test
    void leavesOtherLocalDateTimeInputToTheFormatter() {
        rejects(IsoTemporalFormat::parseLocalDateTime,
        "",
        "2026",
        "2026-11-20t11:38:06",
        "2026-11-20T11:38:06z",
        "+2026-11-20T11:38:06",
        "12026-11-20T11:38:06",
        "2026-11-20T24:00:00",
        "2026-11-20T23:59:60",
        "2026-02-30T10:00:00",
        "2026-13-01T10:00:00",
        "2026-11-20T11:38:06.",
        "2026-11-20T11:38:06.1234567890",
        "2026-11-20T11:38:6",
        "2026-11-20T11:38:06 ",
        "2026-11-20T11:38:06+01:00",
        "2026-11-20T11:38:06[Europe/Prague]",
        "2026-11-20 11:38:06",
        "2026-1-20T11:38:06",
        "١٩٧٠-01-01T00:00:00");
    }

    @Test
    void leavesOtherInstantInputToTheFormatter() {
        rejects(IsoTemporalFormat::parseInstant,
        "2026-11-20T11:38Z",
        "2026-11-20T11:38:06+01:00",
        "2026-11-20T11:38:06z",
        "2026-11-20T23:59:60Z",
        "2026-11-20T11:38:06",
        "+12026-11-20T11:38:06Z");
    }

    @Test
    void leavesOtherOffsetInputToTheFormatter() {
        rejects(IsoTemporalFormat::parseOffsetDateTime,
        "2026-11-20T11:38:06+1:00",
        "2026-11-20T11:38:06+0100",
        "2026-11-20T11:38:06+01",
        "2026-11-20T11:38:06+19:00",
        "2026-11-20T11:38:06+01:60",
        "2026-11-20T11:38:06",
        "2026-11-20T11:38:06+01:00[Europe/Prague]");
    }

    @Test
    void leavesOtherLocalDateInputToTheFormatter() {
        rejects(IsoTemporalFormat::parseLocalDate, "2026-11-2", "2026-11-200", "2026/11/20", "2026-02-29", "2026-00-10", "-001-01-01");
    }

    private static void rejects(Function<String, ?> fast, String... texts) {
        for (String text : texts) {
            assertNull(fast.apply(text), text);
        }
    }

    @Test
    void neverAcceptsWhatTheFormattersRejectOrReadDifferently() {
        Random random = new Random(1);
        List<String> seeds = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            LocalDateTime dateTime = randomDateTime(random, 0, 9999);
            seeds.add(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.of(dateTime, randomOffset(random))));
            seeds.add(DateTimeFormatter.ISO_INSTANT.format(dateTime.toInstant(ZoneOffset.UTC)));
        }
        for (String seed : seeds) {
            for (int i = 0; i < 200; i++) {
                char[] chars = seed.toCharArray();
                int mutations = 1 + random.nextInt(2);
                for (int m = 0; m < mutations; m++) {
                    chars[random.nextInt(chars.length)] = MUTATIONS.charAt(random.nextInt(MUTATIONS.length()));
                }
                String text = new String(chars);
                String cut = text.substring(0, random.nextInt(text.length() + 1));
                for (String candidate : List.of(text, cut)) {
                    check(candidate, DateTimeFormatter.ISO_INSTANT, Instant::from, IsoTemporalFormat::parseInstant);
                    check(candidate, DateTimeFormatter.ISO_DATE_TIME, LocalDateTime::from, IsoTemporalFormat::parseLocalDateTime);
                    check(candidate, DateTimeFormatter.ISO_OFFSET_DATE_TIME, OffsetDateTime::from, IsoTemporalFormat::parseOffsetDateTime);
                    if (candidate.length() >= 10) {
                        String date = candidate.substring(0, 10);
                        check(date, DateTimeFormatter.ISO_LOCAL_DATE, LocalDate::from, IsoTemporalFormat::parseLocalDate);
                    }
                }
            }
        }
    }

    @Test
    void plainDecimals() {
        for (String text : List.of("0", "-1", "1763638686123", "1763638686.123456789", "-0.5")) {
            assertTrue(NumericSupportTemporalSerde.isPlainDecimal(text), text);
        }
        for (String text : List.of("", "-", ".5", "5.", "1.2.3", "+5", "1e3", "1,0", " 1", "2026-11-20")) {
            assertFalse(NumericSupportTemporalSerde.isPlainDecimal(text), text);
        }
    }

    @Test
    void readsPlainNumbersLikeTheFallback() {
        Random random = new Random(3);
        List<String> numbers = new ArrayList<>(List.of("0", "-0", "1", "-1", "999", "-999", "1000", "-1001", "999999999999999999",
            "-999999999999999999", "1763638686123", "-1763638686123", "1763638686.5", "-0.000000001", "1234567890123456789"));
        for (int i = 0; i < 2_000; i++) {
            numbers.add(Long.toString(random.nextLong() >> random.nextInt(64)));
        }
        for (SerdeConfiguration.NumericTimeUnit unit : List.of(SerdeConfiguration.NumericTimeUnit.SECONDS,
            SerdeConfiguration.NumericTimeUnit.MILLISECONDS, SerdeConfiguration.NumericTimeUnit.NANOSECONDS)) {
            InstantNumbers serde = new InstantNumbers(unit);
            for (String number : numbers) {
                assertEquals(outcome(() -> serde.deserializeFallback(new DateTimeException("not a date"), number)),
                    outcome(() -> serde.parseNumber(number)), unit + " " + number);
            }
        }
    }

    private static Object outcome(java.util.function.Supplier<Object> read) {
        try {
            return read.get();
        } catch (ArithmeticException | DateTimeException e) {
            return e.getClass();
        }
    }

    private static final class InstantNumbers extends NumericSupportTemporalSerde<Instant> {
        InstantNumbers(SerdeConfiguration.NumericTimeUnit unit) {
            super(DateTimeFormatter.ISO_INSTANT, DateTimeFormatter.ISO_INSTANT, unit, SerdeConfiguration.TimeShape.STRING, unit);
        }

        @Override
        protected Instant fromNanos(long seconds, int nanos) {
            return Instant.ofEpochSecond(seconds, nanos);
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
        public TemporalQuery<Instant> query() {
            return Instant::from;
        }
    }

    private static <T> void check(String text, DateTimeFormatter formatter, TemporalQuery<T> query, Function<String, T> fast) {
        T fastValue = fast.apply(text);
        if (fastValue == null) {
            return;
        }
        T formatterValue;
        try {
            formatterValue = formatter.parse(text, query);
        } catch (DateTimeException e) {
            throw new AssertionError("Fast path accepted '" + text + "' as " + fastValue + " but the formatter rejects it", e);
        }
        assertEquals(formatterValue, fastValue, text);
    }

    private static LocalDateTime randomDateTime(Random random, int minYear, int maxYear) {
        LocalDate date = LocalDate.of(minYear + random.nextInt(maxYear - minYear + 1), 1, 1).plusDays(random.nextInt(366));
        if (date.getYear() > maxYear) {
            date = date.minusDays(1);
        }
        int second = random.nextInt(4) == 0 ? 0 : random.nextInt(60);
        int nano = random.nextInt(3) == 0 ? random.nextInt(1_000_000_000) : NANOS[random.nextInt(NANOS.length)];
        return LocalDateTime.of(date.getYear(), date.getMonth(), date.getDayOfMonth(), random.nextInt(24), random.nextInt(60), second, nano);
    }

    private static ZoneOffset randomOffset(Random random) {
        return switch (random.nextInt(4)) {
            case 0 -> ZoneOffset.UTC;
            case 1 -> ZoneOffset.ofHoursMinutes(random.nextInt(37) - 18 == 18 ? 18 : random.nextInt(35) - 17, 0);
            case 2 -> ZoneOffset.ofTotalSeconds((random.nextInt(2) == 0 ? -1 : 1) * (random.nextInt(18 * 60) * 60));
            default -> ZoneOffset.ofTotalSeconds(random.nextInt(2 * 18 * 3600 + 1) - 18 * 3600);
        };
    }
}
