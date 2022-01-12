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

import java.io.IOException;
import java.time.ZoneId;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQuery;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.config.annotation.SerdeConfig;

@Internal
final class FormattedTemporalSerde<T extends TemporalAccessor> implements TemporalSerde<T> {
    private final DateTimeFormatter formatter;
    private final TemporalQuery<T> query;

    FormattedTemporalSerde(@NonNull String pattern,
                           @NonNull AnnotationMetadata annotationMetadata,
                           TemporalQuery<T> query) {

        Locale locale = annotationMetadata.stringValue(SerdeConfig.class, SerdeConfig.LOCALE)
                .map(StringUtils::parseLocale)
                .orElse(null);
        DateTimeFormatter f = locale != null ? DateTimeFormatter.ofPattern(pattern, locale) :
                DateTimeFormatter.ofPattern(pattern);
        final ZoneId zone = annotationMetadata
                .stringValue(SerdeConfig.class, SerdeConfig.TIMEZONE)
                .map(ZoneId::of).orElse(UTC);

        this.formatter = f.withZone(zone);
        this.query = query;
    }

    FormattedTemporalSerde(DateTimeFormatter formatter,
                           TemporalQuery<T> query) {
        this.formatter = formatter;
        this.query = query;
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, T value, Argument<? extends T> type) throws IOException {
        encoder.encodeString(
                formatter.format(value)
        );
    }

    @Override
    public T deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super T> type) throws IOException {
        final String str = decoder.decodeString();
        return formatter.parse(str, query());
    }

    @Override
    public TemporalQuery<T> query() {
        return query;
    }
}
