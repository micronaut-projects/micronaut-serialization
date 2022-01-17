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

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Super class that can be used for the default date/time formatting.
 * @param <T> The temporal type
 * @author gkrocher
 */
public abstract class DefaultFormattedTemporalSerde<T extends TemporalAccessor> implements TemporalSerde<T> {

    private final FormattedTemporalSerde<T> defaultFormat;

    /**
     * Allows configuring a default time format for temporal date/time types.
     *
     * @param configuration The configuration
     */
    protected DefaultFormattedTemporalSerde(@NonNull SerdeConfiguration configuration) {
        String pattern = configuration.getDateFormat().orElse(null);
        if (pattern != null) {
            Locale locale = configuration
                .getLocale()
                .orElse(null);        

            DateTimeFormatter formatter = locale != null ? DateTimeFormatter.ofPattern(pattern, locale) :
                DateTimeFormatter.ofPattern(pattern);
            TimeZone tz = configuration.getTimeZone().orElse(null);
            if (tz != null) {
                formatter = formatter.withZone(tz.toZoneId());
            }
            defaultFormat = new FormattedTemporalSerde<>(
                formatter,
                query()
            );
        } else {
            this.defaultFormat = configuration.isWriteDatesAsTimestamps() ? null : new FormattedTemporalSerde<>(
                    getDefaultFormatter(),
                    query()
            );
        }        
    }

    /**
     * @return The default formatter.
     */
    protected abstract @NonNull DateTimeFormatter getDefaultFormatter();

    @Override
    public final void serialize(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException {
        if (defaultFormat != null) {
            defaultFormat.serialize(
                encoder, 
                context,
                    type, value
            );
        } else {
            serializeWithoutFormat(
                encoder, 
                context, 
                value, 
                type
            );
        }
    }

    @Override
    public final T deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super T> type) throws IOException {
        if (defaultFormat != null) {
            return defaultFormat.deserialize(
                decoder, 
                decoderContext, 
                type
            );
        } else {
            return deserializeNonNullWithoutFormat(
                decoder,
                decoderContext,
                type
            );
        }        
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
    
}
