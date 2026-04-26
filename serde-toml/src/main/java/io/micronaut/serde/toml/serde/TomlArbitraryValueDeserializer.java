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
import io.micronaut.serde.toml.TomlParserDecoder;
import io.micronaut.serde.toml.support.SerdeTomlConfiguration;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.regex.Pattern;

/**
 * TOML wrapper for generic Object value feature toggles.
 *
 * @param <T> The deserialized type
 */
@Internal
public final class TomlArbitraryValueDeserializer<T> implements Deserializer<T> {
    private static final Pattern OFFSET_SUFFIX = Pattern.compile(".*(?:Z|[+-]\\d{2}:\\d{2})$");

    private final Deserializer<? extends T> delegate;
    private final Argument<? extends T> type;
    private final LimitingStream.RemainingLimits limits;
    private final SerdeTomlConfiguration tomlConfiguration;

    private TomlArbitraryValueDeserializer(Deserializer<? extends T> delegate,
                                           Argument<? extends T> type,
                                           LimitingStream.RemainingLimits limits,
                                           SerdeTomlConfiguration tomlConfiguration) {
        this.delegate = delegate;
        this.type = type;
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
        return new TomlArbitraryValueDeserializer<>(delegate, type, limits, tomlConfiguration);
    }

    @Override
    public Deserializer<T> createSpecific(Deserializer.DecoderContext context, Argument<? super T> specificType) throws SerdeException {
        @SuppressWarnings("unchecked") Deserializer<? extends T> specificDelegate = (Deserializer<? extends T>) delegate.createSpecific(context, specificType);
        @SuppressWarnings("unchecked") Argument<? extends T> narrowedType = (Argument<? extends T>) specificType;
        return wrap(specificDelegate, narrowedType, limits, tomlConfiguration);
    }

    @Override
    public @Nullable T deserialize(Decoder decoder, Deserializer.DecoderContext context, Argument<? super T> targetType) throws IOException {
        return deserializeNullable(decoder, context, targetType);
    }

    @Override
    public @Nullable T deserializeNullable(Decoder decoder, Deserializer.DecoderContext context, Argument<? super T> targetType) throws IOException {
        if (decoder instanceof TomlParserDecoder tomlParserDecoder && tomlParserDecoder.hasEmbeddedObjectValue()) {
            @SuppressWarnings("unchecked") T embedded = (T) tomlParserDecoder.decodeEmbeddedObject();
            return embedded;
        }
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
