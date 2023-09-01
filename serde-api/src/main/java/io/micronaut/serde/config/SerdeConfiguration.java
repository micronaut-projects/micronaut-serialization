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
package io.micronaut.serde.config;

import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.serde.LimitingStream;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;

/**
 *
 * @author gkrocher
 */
public interface SerdeConfiguration {

    String PREFIX = "micronaut.serde";

    /**
     * The date format to use when serializing and deserializing dates.
     *
     * @return the date format to use
     */
    Optional<String> getDateFormat();

    /**
     * Shape for serializing dates.
     *
     * @return The date serialization shape
     */
    @Bindable(defaultValue = "STRING")
    TimeShape getTimeWriteShape();

    /**
     * The unit to use for serializing and deserializing dates to or from numbers. Note that
     * {@link java.time.LocalDate} always uses the epoch day, regardless of this setting.
     *
     * @return The time unit
     */
    @Bindable(defaultValue = "SECONDS")
    NumericTimeUnit getNumericTimeUnit();

    /**
     * Control whether to use legacy behavior for writing byte arrays. When set to {@code true} (the
     * default in serde 2.x), byte arrays will always be written as arrays of numbers. When set to
     * {@code false}, the encoding may be format-specific instead, and will be a base64 string for
     * JSON.
     *
     * @return Whether to use legacy byte array writing behavior
     */
    @Bindable(defaultValue = "true")
    boolean isWriteBinaryAsArray();

    /**
     * @return The default locale to use.
     */
    Optional<Locale> getLocale();

    /**
     * @return The default time zone to use.
     */
    Optional<TimeZone> getTimeZone();

    /**
     * The packages containing introspections that should be regarded
     * as serializable by default without the need to add the {@link io.micronaut.serde.annotation.Serdeable} annotation.
     *
     * @return the packages to include
     */
    @Bindable(defaultValue = "io.micronaut")
    List<String> getIncludedIntrospectionPackages();

    /**
     * The maximum nesting depth for serialization and deserialization.
     *
     * @return The maximum nesting depth for serialization and deserialization
     * @since 2.0.0
     */
    @Bindable(defaultValue = LimitingStream.DEFAULT_MAXIMUM_DEPTH + "")
    int getMaximumNestingDepth();

    /**
     * Shape to use for time serialization.
     *
     * @since 2.0.0
     */
    enum TimeShape {
        /**
         * Serialize as a string, either using {@link #getDateFormat()} or as an ISO timestamp.
         */
        STRING,
        /**
         * Serialize as an integer. This exists for compatibility, if possible prefer
         * {@link #DECIMAL} so that no part of the time component is lost.
         */
        INTEGER,
        /**
         * Serialize as a decimal value with best possible precision.
         */
        DECIMAL,
    }

    /**
     * Time unit to use when deserializing a numeric value, or when serializing to a numeric value
     * as configured by {@link #getTimeWriteShape()}.
     *
     * @since 2.0.0
     */
    enum NumericTimeUnit {
        /**
         * Legacy unit for compatibility with documents created by micronaut-serialization 1.x
         * (micronaut-core 3.x).
         */
        LEGACY,
        /**
         * Serialize as seconds, the default. Nanoseconds may still be represented as the
         * fractional part.
         */
        SECONDS,
        /**
         * Serialize as milliseconds. Nanoseconds may still be represented as the fractional part.
         */
        MILLISECONDS,
        /**
         * Serialize as nanoseconds. Never has a fractional part (it's ignored if there is one
         * while parsing).
         */
        NANOSECONDS,
    }
}
