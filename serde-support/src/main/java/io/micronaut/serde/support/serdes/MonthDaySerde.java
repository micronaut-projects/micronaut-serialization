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
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;

/**
 * Serde mapping for {@link MonthDay}.
 */
@Internal
final class MonthDaySerde implements FormattedSerde<MonthDay>, SerdeRegistrar<MonthDay> {
    private static final Argument<MonthDay> ARGUMENT = Argument.of(MonthDay.class);

    @Nullable
    private final DateTimeFormatter formatter;
    private final boolean arrayShape;

    MonthDaySerde(SerdeConfiguration configuration) {
        this(DefaultFormattedTemporalSerde.createFormatter(configuration).orElse(null), false);
    }

    private MonthDaySerde(@Nullable DateTimeFormatter formatter, boolean arrayShape) {
        this.formatter = formatter;
        this.arrayShape = arrayShape;
    }

    @Override
    public Serializer<MonthDay> createSpecific(EncoderContext context, Argument<? extends MonthDay> type) {
        context = SerdeFeatures.withFeatures(context, type.getAnnotationMetadata());
        FormatConfiguration format = FormatConfiguration.from(type.getAnnotationMetadata());
        return format == null ? this : createSpecific(context, type, format);
    }

    @Override
    public Serializer<MonthDay> createSpecific(EncoderContext context,
                                               Argument<? extends MonthDay> type,
                                               FormatConfiguration format) {
        return new MonthDaySerde(formatter(format), TemporalArrayShapeSupport.isNumericOrArrayShape(format));
    }

    @Override
    public Deserializer<MonthDay> createSpecific(DecoderContext context, Argument<? super MonthDay> type)
        throws SerdeException {
        context = SerdeFeatures.withFeatures(context, type.getAnnotationMetadata());
        FormatConfiguration format = FormatConfiguration.from(type.getAnnotationMetadata());
        return format == null ? this : createSpecific(context, type, format);
    }

    @Override
    public Deserializer<MonthDay> createSpecific(DecoderContext context,
                                                 Argument<? super MonthDay> type,
                                                 FormatConfiguration format) throws SerdeException {
        return new MonthDaySerde(formatter(format), TemporalArrayShapeSupport.isNumericOrArrayShape(format));
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends MonthDay> type, MonthDay value) throws IOException {
        if (arrayShape) {
            try (Encoder arrayEncoder = encoder.encodeArray(type)) {
                arrayEncoder.encodeInt(value.getMonthValue());
                arrayEncoder.encodeInt(value.getDayOfMonth());
            }
            return;
        }
        encoder.encodeString(formatter == null ? value.toString() : formatter.format(value));
    }

    @Override
    public MonthDay deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super MonthDay> type) throws IOException {
        if (arrayShape) {
            try (Decoder arrayDecoder = decoder.decodeArray(Argument.INT)) {
                MonthDay value = MonthDay.of(arrayDecoder.decodeInt(), arrayDecoder.decodeInt());
                if (arrayDecoder.hasNextArrayValue()) {
                    throw decoder.createDeserializationException("Expected MonthDay array with month and day", null);
                }
                return value;
            }
        }
        String value = decoder.decodeString();
        return formatter == null ? MonthDay.parse(value) : MonthDay.parse(value, formatter);
    }

    @Override
    public @Nullable MonthDay deserializeNullable(Decoder decoder, DecoderContext context, Argument<? super MonthDay> type) throws IOException {
        if (decoder.decodeNull()) {
            return null;
        }
        return deserialize(decoder, context, type);
    }

    @Override
    public Argument<MonthDay> getType() {
        return ARGUMENT;
    }

    @Nullable
    private DateTimeFormatter formatter(FormatConfiguration format) {
        return format.createDateTimeFormatter().orElse(formatter);
    }

}
