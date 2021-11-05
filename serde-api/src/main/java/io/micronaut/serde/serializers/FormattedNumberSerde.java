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
package io.micronaut.serde.serializers;

import java.io.IOException;
import java.text.DecimalFormat;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.exceptions.SerdeException;

/**
 * Adapts serialization for formatted numbers.
 * @param <N> The number type
 */
@Internal
final class FormattedNumberSerde<N extends Number> implements NumberSerde<N> {
    private final String pattern;

    FormattedNumberSerde(String pattern) {
        this.pattern = pattern;
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, N value, Argument<? extends N> type) throws IOException {
        final DecimalFormat decimalFormat = new DecimalFormat(pattern);
        final String result = decimalFormat.format(value);
        encoder.encodeString(result);
    }

    @Override
    public N deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super N> type) throws IOException {
        final String s = decoder.decodeString();
        final DecimalFormat decimalFormat = new DecimalFormat(pattern);
        try {
            final Number number = decimalFormat.parse(s);
            return (N) ConversionService.SHARED.convertRequired(number, type);
        } catch (Exception e) {
            throw new SerdeException("Error decoding number of type " + type + " using pattern " + pattern + ":" + e.getMessage(), e);
        }
    }
}
