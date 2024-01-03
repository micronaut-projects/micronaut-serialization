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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.exceptions.SerdeException;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;

/**
 * Super class that can be used for the default date/time formatting.
 * @param <T> The temporal type
 * @author gkrocher
 */
public abstract class DefaultFormattedTemporalSerde<T extends TemporalAccessor> implements TemporalSerde<T> {

    private final DateTimeFormatter stringFormatter;

    /**
     * Allows configuring a default time format for temporal date/time types.
     *
     * @param configuration          The configuration
     * @param defaultStringFormatter Default string formatter to use if the user hasn't configured one
     */
    protected DefaultFormattedTemporalSerde(
        @NonNull SerdeConfiguration configuration,
        @NonNull DateTimeFormatter defaultStringFormatter
    ) {
        stringFormatter = createFormatter(configuration).orElse(defaultStringFormatter);
    }

    @Override
    public Serializer<T> createSpecific(EncoderContext context, Argument<? extends T> type) {
        return createSpecific(context.getSerdeConfiguration());
    }

    @Override
    public Deserializer<T> createSpecific(DecoderContext decoderContext, Argument<? super T> context) throws SerdeException {
        return createSpecific(decoderContext.getSerdeConfiguration());
    }

    /**
     * Create the same serde with new configuration.
     *
     * @param configuration The new configuration
     * @return The updated serde
     */
    protected DefaultFormattedTemporalSerde<T> createSpecific(SerdeConfiguration configuration) {
        return this;
    }

    @Override
    public final void serialize(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException {
        serialize0(encoder, value);
    }

    /**
     * Serialize method, can be overridden to support numeric serialization.
     *
     * @param encoder The encoder
     * @param value   The value to serialize
     */
    void serialize0(Encoder encoder, T value) throws IOException {
        encoder.encodeString(stringFormatter.format(value));
    }

    @Override
    public final T deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super T> type) throws IOException {
        String text = decoder.decodeString();
        try {
            return stringFormatter.parse(text, query());
        } catch (DateTimeException e) {
            return deserializeFallback(e, text);
        }
    }

    /**
     * Fallback to try when parsing as a timestamp fails.
     *
     * @param exc The parse exception, for rethrowing
     * @param s   The input value
     * @return The parsed value
     */
    T deserializeFallback(DateTimeException exc, String s) {
        throw exc;
    }

    @NonNull
    private static Optional<DateTimeFormatter> createFormatter(@NonNull SerdeConfiguration configuration) {
        // Creates a pattern-based formatter if there is a date format configured
        return configuration.getDateFormat()
            .map(pattern -> configuration.getLocale()
                .map(locale -> DateTimeFormatter.ofPattern(pattern, locale))
                    .orElseGet(() -> DateTimeFormatter.ofPattern(pattern)))
            .map(formatter -> configuration.getTimeZone()
                    .map(tz -> formatter.withZone(tz.toZoneId()))
                    .orElse(formatter));
    }
}
