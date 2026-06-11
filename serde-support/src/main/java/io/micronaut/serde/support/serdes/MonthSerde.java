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
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.SerdeRegistrar;
import io.micronaut.serde.support.util.SerdeFeatures;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.Month;
import java.time.format.DateTimeFormatter;

/**
 * Serde mapping for {@link Month}.
 */
@Internal
final class MonthSerde implements FormattedSerde<Month>, SerdeRegistrar<Month> {
    private static final Argument<Month> ARGUMENT = Argument.of(Month.class);

    @Nullable
    private final DateTimeFormatter formatter;
    private final boolean arrayShape;

    MonthSerde() {
        this(null, false);
    }

    private MonthSerde(@Nullable DateTimeFormatter formatter, boolean arrayShape) {
        this.formatter = formatter;
        this.arrayShape = arrayShape;
    }

    @Override
    public Serializer<Month> createSpecific(EncoderContext context, Argument<? extends Month> type) {
        context = SerdeFeatures.withFeatures(context, type.getAnnotationMetadata());
        FormatConfiguration format = FormatConfiguration.from(type.getAnnotationMetadata());
        return format == null ? this : createSpecific(context, type, format);
    }

    @Override
    public Serializer<Month> createSpecific(EncoderContext context,
                                            Argument<? extends Month> type,
                                            FormatConfiguration format) {
        return new MonthSerde(format.createDateTimeFormatter().orElse(formatter), TemporalArrayShapeSupport.isNumericOrArrayShape(format));
    }

    @Override
    public Deserializer<Month> createSpecific(DecoderContext context, Argument<? super Month> type)
        throws SerdeException {
        context = SerdeFeatures.withFeatures(context, type.getAnnotationMetadata());
        FormatConfiguration format = FormatConfiguration.from(type.getAnnotationMetadata());
        return format == null ? this : createSpecific(context, type, format);
    }

    @Override
    public Deserializer<Month> createSpecific(DecoderContext context,
                                              Argument<? super Month> type,
                                              FormatConfiguration format) throws SerdeException {
        return new MonthSerde(format.createDateTimeFormatter().orElse(formatter), TemporalArrayShapeSupport.isNumericOrArrayShape(format));
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Month> type, Month value) throws IOException {
        if (arrayShape) {
            try (Encoder arrayEncoder = encoder.encodeArray(type)) {
                serializeScalar(arrayEncoder, value);
            }
            return;
        }
        serializeScalar(encoder, value);
    }

    @Override
    public Month deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Month> type) throws IOException {
        if (arrayShape) {
            try (Decoder arrayDecoder = decoder.decodeArray(formatter == null ? Argument.INT : Argument.STRING)) {
                Month value = deserializeScalar(arrayDecoder);
                if (arrayDecoder.hasNextArrayValue()) {
                    throw decoder.createDeserializationException("Expected Month array with one value", null);
                }
                return value;
            }
        }
        return deserializeScalar(decoder);
    }

    @Override
    public @Nullable Month deserializeNullable(Decoder decoder, DecoderContext context, Argument<? super Month> type) throws IOException {
        if (decoder.decodeNull()) {
            return null;
        }
        return deserialize(decoder, context, type);
    }

    @Override
    public Argument<Month> getType() {
        return ARGUMENT;
    }

    private void serializeScalar(Encoder encoder, Month value) throws IOException {
        if (formatter == null) {
            encoder.encodeInt(value.getValue());
        } else {
            encoder.encodeString(formatter.format(value));
        }
    }

    private Month deserializeScalar(Decoder decoder) throws IOException {
        if (formatter == null) {
            return month(decoder, decoder.decodeInt());
        }
        return month(decoder, decoder.decodeString());
    }

    private Month month(Decoder decoder, String value) throws IOException {
        if (formatter != null) {
            try {
                return Month.from(formatter.parse(value));
            } catch (DateTimeException e) {
                throw decoder.createDeserializationException("Invalid Month value", value);
            }
        }
        try {
            return month(decoder, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            // Fall through to enum-name handling.
        }
        try {
            return Month.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw decoder.createDeserializationException("Invalid Month value", value);
        }
    }

    private static Month month(Decoder decoder, int value) throws IOException {
        try {
            return Month.of(value);
        } catch (DateTimeException e) {
            throw decoder.createDeserializationException("Month number outside 1-12 range", value);
        }
    }
}
