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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import org.jspecify.annotations.Nullable;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.FormatConfiguration;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQuery;

@Internal
final class FormattedTemporalSerde<T extends TemporalAccessor> implements TemporalSerde<T> {
    final DateTimeFormatter formatter;
    final TemporalQuery<T> query;
    final TemporalSerde<T> originalTemporalSerde;
    @Nullable
    final ZoneId adjustTimeZone;

    FormattedTemporalSerde(DateTimeFormatter formatter,
                           FormatConfiguration format,
                           TemporalQuery<T> query,
                           TemporalSerde<T> originalTemporalSerde,
                           boolean adjustDatesToContextTimeZone) {
        this.formatter = formatter;
        this.query = query;
        this.originalTemporalSerde = originalTemporalSerde;
        this.adjustTimeZone = adjustDatesToContextTimeZone
            ? format.parseTimeZone().toZoneId()
            : null;
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException {
        encoder.encodeString(
                formatter.format(value)
        );
    }

    @Override
    public T deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super T> type) throws IOException {
        final String str = decoder.decodeString();
        try {
            return adjust(formatter.parse(str, query()));
        } catch (DateTimeException e) {
            if (originalTemporalSerde instanceof DefaultFormattedTemporalSerde<T> defaultFormattedTemporalSerde) {
                return defaultFormattedTemporalSerde.deserializeFallback(e, str);
            } else {
                throw e;
            }
        }
    }

    @Override
    public TemporalQuery<T> query() {
        return query;
    }

    @SuppressWarnings("unchecked")
    private T adjust(T value) {
        if (adjustTimeZone == null) {
            return value;
        }
        if (value instanceof ZonedDateTime zonedDateTime) {
            return (T) zonedDateTime.withZoneSameInstant(adjustTimeZone);
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return (T) offsetDateTime.atZoneSameInstant(adjustTimeZone).toOffsetDateTime();
        }
        return value;
    }
}
