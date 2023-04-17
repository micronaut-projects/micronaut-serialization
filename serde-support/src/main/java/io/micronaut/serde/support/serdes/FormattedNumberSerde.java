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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serde;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Adapts serialization for formatted numbers.
 * @param <N> The number type
 */
@Internal
final class FormattedNumberSerde<N extends Number> implements Serde<N> {
    private final String pattern;
    private final Locale locale;

    FormattedNumberSerde(@NonNull String pattern, @NonNull AnnotationMetadata annotationMetadata) {
        this.pattern = pattern;
        this.locale = annotationMetadata.stringValue(SerdeConfig.class, SerdeConfig.LOCALE)
                .map(StringUtils::parseLocale)
                .orElse(null);
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends N> type, N value) throws IOException {
        final DecimalFormat decimalFormat = createDecimalFormat(type);
        final String result = decimalFormat.format(value);
        encoder.encodeString(result);
    }

    @Override
    public N deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super N> type) throws IOException {
        final String s = decoder.decodeString();
        final DecimalFormat decimalFormat = createDecimalFormat(type);
        try {
            final Number number = decimalFormat.parse(s);
            return (N) decoderContext.getConversionService().convertRequired(number, type);
        } catch (Exception e) {
            throw new SerdeException("Error decoding number of type " + type + " using pattern " + pattern + ":" + e.getMessage(), e);
        }
    }

    private DecimalFormat createDecimalFormat(Argument<?> type) throws SerdeException {
        final DecimalFormat decimalFormat;
        try {
            if (locale != null) {
                decimalFormat = (DecimalFormat) NumberFormat.getInstance(locale);
                decimalFormat.applyPattern(pattern);
            } else {
                decimalFormat = new DecimalFormat(pattern);
            }
        } catch (Exception e) {
            throw new SerdeException("Error decoding number of type " + type + ", pattern is invalid " + pattern + ":" + e.getMessage(), e);
        }
        return decimalFormat;
    }
}
