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
import io.micronaut.serde.Encoder;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.exceptions.InvalidFormatException;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;

/**
 * Super class that can be used for the default date/time formatting.
 * @param <T> The temporal type
 * @author gkrocher
 */
public abstract class DefaultFormattedTemporalSerde<T extends TemporalAccessor> implements TemporalSerde<T> {

    private final FormattedTemporalSerde<T> defaultFormat;
    private final boolean treatDatesAsTimestamps;

    /**
     * Allows configuring a default time format for temporal date/time types.
     *
     * @param configuration The configuration
     */
    protected DefaultFormattedTemporalSerde(@NonNull SerdeConfiguration configuration) {
        defaultFormat = new FormattedTemporalSerde<>(getFormatter(configuration), query());
        treatDatesAsTimestamps = configuration.isWriteDatesAsTimestamps() && !configuration.getDateFormat().isPresent();
    }

    /**
     * @return The default formatter.
     */
    protected abstract @NonNull DateTimeFormatter getDefaultFormatter();

    @Override
    public final void serialize(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException {
        if (treatDatesAsTimestamps) {
            serializeWithoutFormat(encoder, context, value, type);
        } else {
            defaultFormat.serialize(encoder, context, type, value);
        }
    }

    @Override
    public final T deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super T> type) throws IOException {
        if (treatDatesAsTimestamps) {
            try {
                return deserializeNonNullWithoutFormat(decoder, decoderContext, type);
            } catch (InvalidFormatException e) {
                // The property was not serialized as a timestamp.
                // We will ignore this error and try to deserialize it as a string.
            }
        }
        return defaultFormat.deserialize(decoder, decoderContext, type);
    }

    /**
     * Serializes the given value using the passed {@link Encoder}.
     * @param encoder The encoder to use
     * @param context The encoder context, never {@code null}
     * @param value The value, can be {@code null}
     * @param type Models the generic type of the value
     * @throws IOException If an error occurs during serialization
     */
    protected abstract void serializeWithoutFormat(Encoder encoder, EncoderContext context, T value, Argument<? extends T> type) throws IOException;

    /**
     * A method that is invoked when the value is known not to be null.
     * @param decoder The decoder
     * @param decoderContext The decoder context
     * @param type The type
     * @return The value
     * @throws IOException if something goes wrong during deserialization
     */
    protected abstract T deserializeNonNullWithoutFormat(Decoder decoder, DecoderContext decoderContext, Argument<? super T> type) throws IOException;

    @NonNull
    private DateTimeFormatter getFormatter(@NonNull SerdeConfiguration configuration) {
        // Creates a custom formatter or returns the default one
        return this.createFormatter(configuration)
            .orElseGet(this::getDefaultFormatter);
    }

    @NonNull
    private Optional<DateTimeFormatter> createFormatter(@NonNull SerdeConfiguration configuration) {
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
