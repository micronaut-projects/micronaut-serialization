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
package io.micronaut.serde.support.serdes;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.FormatConfiguration;
import io.micronaut.serde.FormattedSerde;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.SerdeRegistrar;
import io.micronaut.serde.support.util.SerdeFeatures;

import java.io.IOException;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/**
 * Serde mapping for {@link YearMonth}.
 */
@Internal
final class YearMonthSerde implements FormattedSerde<YearMonth>, SerdeRegistrar<YearMonth> {
    private static final Argument<YearMonth> ARGUMENT = Argument.of(YearMonth.class);
    private static final DateTimeFormatter DEFAULT_FORMATTER = DateTimeFormatter.ofPattern("u-MM");

    private final DateTimeFormatter formatter;
    private final boolean arrayShape;

    YearMonthSerde(SerdeConfiguration configuration) {
        this(
            DefaultFormattedTemporalSerde.createFormatter(configuration).orElse(DEFAULT_FORMATTER),
            configuration.getTimeWriteShape() != SerdeConfiguration.TimeShape.STRING
        );
    }

    private YearMonthSerde(DateTimeFormatter formatter, boolean arrayShape) {
        this.formatter = formatter;
        this.arrayShape = arrayShape;
    }

    @Override
    public Serializer<YearMonth> createSpecific(EncoderContext context, Argument<? extends YearMonth> type) {
        context = SerdeFeatures.withFeatures(context, type.getAnnotationMetadata());
        FormatConfiguration format = FormatConfiguration.from(type.getAnnotationMetadata());
        return format == null ? this : createSpecific(context, type, format);
    }

    @Override
    public Serializer<YearMonth> createSpecific(EncoderContext context,
                                                Argument<? extends YearMonth> type,
                                                FormatConfiguration format) {
        return new YearMonthSerde(formatter(format), useArrayShape(format));
    }

    @Override
    public Deserializer<YearMonth> createSpecific(DecoderContext context, Argument<? super YearMonth> type)
        throws SerdeException {
        context = SerdeFeatures.withFeatures(context, type.getAnnotationMetadata());
        FormatConfiguration format = FormatConfiguration.from(type.getAnnotationMetadata());
        return format == null ? this : createSpecific(context, type, format);
    }

    @Override
    public Deserializer<YearMonth> createSpecific(DecoderContext context,
                                                  Argument<? super YearMonth> type,
                                                  FormatConfiguration format) throws SerdeException {
        return new YearMonthSerde(formatter(format), useArrayShape(format));
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends YearMonth> type, YearMonth value) throws IOException {
        if (arrayShape) {
            try (Encoder arrayEncoder = encoder.encodeArray(type)) {
                arrayEncoder.encodeInt(value.getYear());
                arrayEncoder.encodeInt(value.getMonthValue());
            }
            return;
        }
        encoder.encodeString(formatter.format(value));
    }

    @Override
    public YearMonth deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super YearMonth> type) throws IOException {
        if (arrayShape) {
            try (Decoder arrayDecoder = decoder.decodeArray(Argument.INT)) {
                YearMonth value = YearMonth.of(arrayDecoder.decodeInt(), arrayDecoder.decodeInt());
                if (arrayDecoder.hasNextArrayValue()) {
                    throw decoder.createDeserializationException("Expected YearMonth array with year and month", null);
                }
                return value;
            }
        }
        return YearMonth.parse(decoder.decodeString(), formatter);
    }

    @Override
    public Argument<YearMonth> getType() {
        return ARGUMENT;
    }

    private DateTimeFormatter formatter(FormatConfiguration format) {
        return format.createDateTimeFormatter().orElse(formatter);
    }

    private boolean useArrayShape(FormatConfiguration format) {
        if (format.shape() == FormatConfiguration.Shape.STRING) {
            return false;
        }
        if (TemporalArrayShapeSupport.isNumericOrArrayShape(format)) {
            return true;
        }
        return format.pattern() == null && arrayShape;
    }

}
