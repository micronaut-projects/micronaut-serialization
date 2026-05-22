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
package io.micronaut.serde.toml.serde;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.util.JsonNodeDecoder;
import io.micronaut.serde.toml.support.SerdeTomlConfiguration;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.regex.Pattern;

/**
 * Deserializer wrapper that materializes TOML temporal strings into Java time objects
 * when the target type is {@code Object} and {@code parse-java-time} is enabled.
 *
 * <p>The micronaut-toml parser produces {@link JsonNode} string values for
 * <a href="https://toml.io/en/v1.0.0#offset-date-time">TOML date-time types</a>
 * (Offset Date-Time, Local Date-Time, Local Date, Local Time). The default
 * micronaut-serde deserializer would leave these as plain strings when deserializing
 * into {@code Object} or {@code Map<String, Object>}. This wrapper detects temporal
 * patterns and converts them to the appropriate Java type.</p>
 *
 * <p>Conversion rules when {@code parse-java-time: true}:</p>
 * <ul>
 *   <li>{@code 2021-03-26} → {@link java.time.LocalDate}</li>
 *   <li>{@code 18:40:15.123} → {@link java.time.LocalTime}</li>
 *   <li>{@code 2021-03-26T18:40:15} → {@link java.time.LocalDateTime}</li>
 *   <li>{@code 2021-03-26T18:40:15+01:00} → {@link java.time.OffsetDateTime}</li>
 * </ul>
 *
 * <p>Example configuration in {@code application.yml}:</p>
 * <pre>{@code
 * micronaut:
 *   serde:
 *     toml:
 *       read-features:
 *         parse-java-time: true
 * }</pre>
 *
 * @param <T> The deserialized type
 */
@Internal
public final class TomlArbitraryValueDeserializer<T> implements Deserializer<T> {
    /**
     * Matches the RFC 3339 offset suffix of a TOML Offset Date-Time:
     * {@code Z}, {@code +HH:MM}, or {@code -HH:MM}.
     *
     * @see <a href="https://toml.io/en/v1.0.0#offset-date-time">TOML v1.0.0 Offset Date-Time</a>
     */
    private static final Pattern OFFSET_SUFFIX = Pattern.compile(".*(?:Z|[+-]\\d{2}:\\d{2})$");

    private final Deserializer<? extends T> delegate;
    private final LimitingStream.RemainingLimits limits;
    private final SerdeTomlConfiguration tomlConfiguration;

    private TomlArbitraryValueDeserializer(Deserializer<? extends T> delegate,
                                           LimitingStream.RemainingLimits limits,
                                           SerdeTomlConfiguration tomlConfiguration) {
        this.delegate = delegate;
        this.limits = limits;
        this.tomlConfiguration = tomlConfiguration;
    }

    public static <T> Deserializer<T> wrap(Deserializer<? extends T> delegate,
                                           Argument<? extends T> type,
                                           LimitingStream.RemainingLimits limits,
                                           @Nullable SerdeTomlConfiguration tomlConfiguration) {
        if (tomlConfiguration == null || !tomlConfiguration.isParseJavaTime() || type.getType() != Object.class) {
            @SuppressWarnings("unchecked") Deserializer<T> cast = (Deserializer<T>) delegate;
            return cast;
        }
        return new TomlArbitraryValueDeserializer<>(delegate, limits, tomlConfiguration);
    }

    @Override
    public Deserializer<T> createSpecific(Deserializer.DecoderContext context, Argument<? super T> specificType) throws SerdeException {
        @SuppressWarnings("unchecked") Deserializer<? extends T> specificDelegate = (Deserializer<? extends T>) delegate.createSpecific(context, specificType);
        @SuppressWarnings("unchecked") Argument<? extends T> narrowedType = (Argument<? extends T>) specificType;
        return wrap(specificDelegate, narrowedType, limits, tomlConfiguration);
    }

    @SuppressWarnings({"unchecked", "NullAway"})
    @Override
    public T deserialize(Decoder decoder, Deserializer.DecoderContext context, Argument<? super T> targetType) throws IOException {
        JsonNode node = decoder.decodeNode();
        if (node.isString()) {
            Object parsedTemporal = tryParseTemporal(node.getStringValue());
            if (parsedTemporal != null) {
                return (T) parsedTemporal;
            }
        }
        Deserializer<T> cast = (Deserializer<T>) delegate;
        return cast.deserialize(JsonNodeDecoder.create(node, limits), context, targetType);
    }

    @Override
    public @Nullable T deserializeNullable(Decoder decoder, Deserializer.DecoderContext context, Argument<? super T> targetType) throws IOException {
        JsonNode node = decoder.decodeNode();
        if (node.isString()) {
            Object parsedTemporal = tryParseTemporal(node.getStringValue());
            if (parsedTemporal != null) {
                @SuppressWarnings("unchecked") T cast = (T) parsedTemporal;
                return cast;
            }
        }
        @SuppressWarnings("unchecked") Deserializer<T> cast = (Deserializer<T>) delegate;
        return cast.deserializeNullable(JsonNodeDecoder.create(node, limits), context, targetType);
    }

    @Override
    public @Nullable T getDefaultValue(Deserializer.DecoderContext context, Argument<? super T> targetType) {
        @SuppressWarnings("unchecked") Deserializer<T> cast = (Deserializer<T>) delegate;
        return cast.getDefaultValue(context, targetType);
    }

    @Nullable
    private static Object tryParseTemporal(String value) {
        // detect LocalDate
        if (value.length() == 10 && value.charAt(4) == '-' && value.charAt(7) == '-') {
            try {
                return LocalDate.parse(value);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        // detect LocalTime
        if (value.indexOf(':') >= 0 && value.indexOf('-') < 0 && value.indexOf('T') < 0 && value.indexOf(' ') < 0) {
            try {
                return LocalTime.parse(value);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        String normalized = normalizeDateTimeSeparator(value);
        // detect datetime
        if (normalized.indexOf('T') >= 0) {
            if (OFFSET_SUFFIX.matcher(normalized).matches()) {
                try {
                    return OffsetDateTime.parse(normalized);
                } catch (RuntimeException ignored) {
                    // try non-offset parsing below
                }
            }
            try {
                return LocalDateTime.parse(normalized);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    // normalize space-separated datetime
    private static String normalizeDateTimeSeparator(String value) {
        if (value.length() > 10 && value.charAt(10) == ' ') {
            return value.substring(0, 10) + 'T' + value.substring(11);
        }
        return value;
    }
}
