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
package io.micronaut.serde.jsonb;

import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.Set;
import java.util.TimeZone;

/**
 * Shared JSON-B scalar type classifier for generated and reflection-provider
 * routing decisions.
 * <p>
 * Keep the exact-type sets biased toward final JDK types. Extensible categories
 * such as {@link Number}, {@link CharSequence}, {@link Date}, {@link Calendar},
 * {@link TimeZone}, and {@link ZoneId} deliberately remain assignability checks
 * so user subclasses keep the same routing behavior.
 */
final class JsonbScalarTypes {
    private static final Set<Class<?>> JSON_SCALAR_TYPES = Set.of(
        Boolean.class,
        Character.class,
        String.class,
        URI.class,
        URL.class,
        Instant.class,
        Duration.class,
        Period.class,
        LocalDate.class,
        LocalTime.class,
        LocalDateTime.class,
        ZonedDateTime.class,
        OffsetDateTime.class,
        OffsetTime.class
    );

    private static final Set<Class<?>> JSON_DATE_TIME_SCALAR_TYPES = Set.of(
        Instant.class,
        Duration.class,
        Period.class,
        LocalDate.class,
        LocalTime.class,
        LocalDateTime.class,
        ZonedDateTime.class,
        OffsetDateTime.class,
        OffsetTime.class
    );

    private JsonbScalarTypes() {
    }

    /**
     * Tests whether a type should be handled by a scalar JSON-B Serde instead
     * of runtime bean introspection.
     *
     * @param type The type to test
     * @return Whether the type is scalar for JSON-B routing purposes
     */
    static boolean isJsonScalar(Class<?> type) {
        return type.isPrimitive()
            || JSON_SCALAR_TYPES.contains(type)
            || CharSequence.class.isAssignableFrom(type)
            || Number.class.isAssignableFrom(type)
            || Enum.class.isAssignableFrom(type)
            || Date.class.isAssignableFrom(type)
            || Calendar.class.isAssignableFrom(type)
            || TimeZone.class.isAssignableFrom(type)
            || ZoneId.class.isAssignableFrom(type);
    }

    /**
     * Tests whether a type needs JSON-B date/time fallback annotations applied
     * to a generated Serde model. This is narrower than {@link #isJsonScalar(Class)}
     * because string, number, enum, and URI-like values do not use date format
     * metadata.
     *
     * @param type The type to test
     * @return Whether the type is a date/time scalar for JSON-B fallback purposes
     */
    static boolean isJsonDateTimeScalar(Class<?> type) {
        return JSON_DATE_TIME_SCALAR_TYPES.contains(type)
            || Date.class.isAssignableFrom(type)
            || Calendar.class.isAssignableFrom(type)
            || TimeZone.class.isAssignableFrom(type)
            || ZoneId.class.isAssignableFrom(type);
    }
}
