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
package io.micronaut.serde;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.util.StringUtils;
import io.micronaut.serde.config.annotation.SerdeConfig;
import org.jspecify.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.TimeZone;

/**
 * Configuration mapped from format metadata.
 *
 * @param pattern  The format pattern
 * @param shape    The shape to use
 * @param locale   The locale to use
 * @param timezone The time zone to use
 * @param lenient  Whether lenient parsing should be used
 * @param radix    The numeric base to use
 * @author Denis Stepanov
 * @since 3.0
 */
public record FormatConfiguration(
    @Nullable String pattern,
    Shape shape,
    @Nullable String locale,
    @Nullable String timezone,
    @Nullable Boolean lenient,
    int radix
) {
    /**
     * The default radix marker.
     */
    public static final int DEFAULT_RADIX = -1;

    /**
     * Empty format configuration.
     */
    public static final FormatConfiguration EMPTY = new FormatConfiguration(
        null,
        Shape.ANY,
        null,
        null,
        null,
        DEFAULT_RADIX
    );

    /**
     * Default constructor.
     */
    public FormatConfiguration {
        Objects.requireNonNull(shape, "shape");
    }

    /**
     * Resolve format configuration from annotation metadata.
     *
     * @param annotationMetadata The annotation metadata
     * @return The format configuration
     */
    @Nullable
    public static FormatConfiguration from(AnnotationMetadata annotationMetadata) {
        String pattern = annotationMetadata.stringValue(SerdeConfig.class, SerdeConfig.PATTERN).orElse(null);
        Shape shape = annotationMetadata.enumValue(SerdeConfig.class, SerdeConfig.SHAPE, Shape.class).orElse(Shape.ANY);
        String locale = annotationMetadata.stringValue(SerdeConfig.class, SerdeConfig.LOCALE).orElse(null);
        String timezone = annotationMetadata.stringValue(SerdeConfig.class, SerdeConfig.TIMEZONE).orElse(null);
        Boolean lenient = annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.LENIENT).orElse(null);
        int radix = annotationMetadata.intValue(SerdeConfig.class, SerdeConfig.RADIX).orElse(DEFAULT_RADIX);
        boolean hasFormat = pattern != null
            || shape != Shape.ANY
            || locale != null
            || timezone != null
            || lenient != null
            || radix != DEFAULT_RADIX;
        if (!hasFormat) {
            return null;
        }
        return new FormatConfiguration(pattern, shape, locale, timezone, lenient, radix);
    }

    /**
     * @return The parsed locale, or {@code null} if no locale is configured
     */
    @Nullable
    public Locale parseLocale() {
        return Optional.ofNullable(locale)
            .map(StringUtils::parseLocale)
            .orElse(null);
    }

    /**
     * @return The parsed time zone, defaulting to UTC if no time zone is configured
     */
    public TimeZone parseTimeZone() {
        return Optional.ofNullable(timezone)
            .map(TimeZone::getTimeZone)
            .orElseGet(() -> TimeZone.getTimeZone("UTC"));
    }

    /**
     * @return A date format when a pattern is configured
     */
    public Optional<SimpleDateFormat> createDateFormat() {
        return Optional.ofNullable(pattern).map(p -> {
            Locale parsedLocale = parseLocale();
            SimpleDateFormat dateFormat = parsedLocale == null ? new SimpleDateFormat(p) : new SimpleDateFormat(p, parsedLocale);
            dateFormat.setTimeZone(parseTimeZone());
            if (lenient != null) {
                dateFormat.setLenient(lenient);
            }
            return dateFormat;
        });
    }

    /**
     * @return A date-time formatter when a pattern is configured
     */
    public Optional<DateTimeFormatter> createDateTimeFormatter() {
        return Optional.ofNullable(pattern).map(p -> {
            Locale parsedLocale = parseLocale();
            DateTimeFormatter formatter = parsedLocale == null
                ? DateTimeFormatter.ofPattern(p)
                : DateTimeFormatter.ofPattern(p, parsedLocale);
            if (Boolean.FALSE.equals(lenient)) {
                formatter = formatter.withResolverStyle(ResolverStyle.STRICT);
            }
            return formatter.withZone(parseTimeZone().toZoneId());
        });
    }

    /**
     * Shape values supported by format metadata.
     */
    public enum Shape {
        /**
         * Binary value shape.
         */
        BINARY,
        /**
         * Boolean scalar shape.
         */
        BOOLEAN,
        /**
         * Numeric scalar shape.
         */
        NUMBER,
        /**
         * Floating-point numeric scalar shape.
         */
        NUMBER_FLOAT,
        /**
         * Integral numeric scalar shape.
         */
        NUMBER_INT,
        /**
         * String scalar shape.
         */
        STRING,
        /**
         * Any scalar shape.
         */
        SCALAR,
        /**
         * Array shape.
         */
        ARRAY,
        /**
         * Object shape.
         */
        OBJECT,
        /**
         * No explicit shape preference.
         */
        ANY,
        /**
         * Natural shape for the value type.
         */
        NATURAL,
        /**
         * POJO-like object shape.
         */
        POJO;

        /**
         * @return {@code true} if this shape is numeric
         */
        public boolean isNumeric() {
            return this == NUMBER || this == NUMBER_INT || this == NUMBER_FLOAT;
        }

        /**
         * @return {@code true} if this shape is structured
         */
        public boolean isStructured() {
            return this == OBJECT || this == ARRAY || this == POJO;
        }

        /**
         * @return {@code true} if this shape should use POJO-like object handling
         * @since 3.0
         */
        public boolean isPojoShape() {
            return this == OBJECT || this == POJO;
        }
    }

}
