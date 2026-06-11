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
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.SerializationConfiguration;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.SerdeRegistrar;
import io.micronaut.serde.support.util.SerdeFeatures;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Serde mapping for {@link OffsetTime}.
 */
@Internal
final class OffsetTimeSerde implements FormattedSerde<OffsetTime>, SerdeRegistrar<OffsetTime> {
    private static final Argument<OffsetTime> ARGUMENT = Argument.of(OffsetTime.class);

    private final DateTimeFormatter formatter;
    private final boolean arrayShape;

    OffsetTimeSerde() {
        this(DateTimeFormatter.ISO_OFFSET_TIME, false);
    }

    OffsetTimeSerde(SerdeConfiguration configuration) {
        this(DateTimeFormatter.ISO_OFFSET_TIME, configuration.getTimeWriteShape() != SerdeConfiguration.TimeShape.STRING);
    }

    private OffsetTimeSerde(DateTimeFormatter formatter, boolean arrayShape) {
        this.formatter = formatter;
        this.arrayShape = arrayShape;
    }

    @Override
    public Serializer<OffsetTime> createSpecific(EncoderContext context, Argument<? extends OffsetTime> type) {
        context = SerdeFeatures.withFeatures(context, type.getAnnotationMetadata());
        FormatConfiguration format = FormatConfiguration.from(type.getAnnotationMetadata());
        return format == null ? this : createSpecific(context, type, format);
    }

    @Override
    public Serializer<OffsetTime> createSpecific(EncoderContext context,
                                                 Argument<? extends OffsetTime> type,
                                                 FormatConfiguration format) {
        if (TemporalArrayShapeSupport.isNumericOrArrayShape(format)) {
            return new OffsetTimeSerde(formatter(format), true);
        }
        return new OffsetTimeSerde(formatter(format), false);
    }

    @Override
    public Deserializer<OffsetTime> createSpecific(DecoderContext context, Argument<? super OffsetTime> type)
        throws SerdeException {
        context = SerdeFeatures.withFeatures(context, type.getAnnotationMetadata());
        FormatConfiguration format = FormatConfiguration.from(type.getAnnotationMetadata());
        return format == null ? this : createSpecific(context, type, format);
    }

    @Override
    public Deserializer<OffsetTime> createSpecific(DecoderContext context,
                                                   Argument<? super OffsetTime> type,
                                                   FormatConfiguration format) throws SerdeException {
        if (TemporalArrayShapeSupport.isNumericOrArrayShape(format)) {
            return new OffsetTimeSerde(formatter(format), true);
        }
        return new OffsetTimeSerde(formatter(format), false);
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends OffsetTime> type, OffsetTime value) throws IOException {
        if (arrayShape) {
            try (Encoder arrayEncoder = encoder.encodeArray(type)) {
                TemporalArrayShapeSupport.serializeLocalTime(
                    arrayEncoder,
                    value.toLocalTime(),
                    context.getFeatures().contains(SerializationConfiguration.Feature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
                );
                arrayEncoder.encodeString(value.getOffset().toString());
            }
            return;
        }
        encoder.encodeString(formatter.format(value));
    }

    @Override
    public OffsetTime deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super OffsetTime> type) throws IOException {
        if (arrayShape) {
            return deserializeArray(decoder, decoderContext);
        }
        return OffsetTime.from(formatter.parse(decoder.decodeString()));
    }

    @Override
    public @Nullable OffsetTime deserializeNullable(Decoder decoder, DecoderContext context, Argument<? super OffsetTime> type) throws IOException {
        if (decoder.decodeNull()) {
            return null;
        }
        return deserialize(decoder, context, type);
    }

    @Override
    public Argument<OffsetTime> getType() {
        return ARGUMENT;
    }

    private DateTimeFormatter formatter(FormatConfiguration format) {
        String pattern = format.pattern();
        if (pattern == null) {
            return formatter;
        }
        Locale locale = format.parseLocale();
        DateTimeFormatter resolved = locale == null
            ? DateTimeFormatter.ofPattern(pattern)
            : DateTimeFormatter.ofPattern(pattern, locale);
        if (Boolean.FALSE.equals(format.lenient())) {
            resolved = resolved.withResolverStyle(ResolverStyle.STRICT);
        }
        if (format.timezone() != null) {
            resolved = resolved.withZone(format.parseTimeZone().toZoneId());
        }
        return resolved;
    }

    private static OffsetTime deserializeArray(Decoder decoder, DecoderContext context) throws IOException {
        List<String> values = new ArrayList<>(5);
        try (Decoder arrayDecoder = decoder.decodeArray(Argument.OBJECT_ARGUMENT)) {
            while (arrayDecoder.hasNextArrayValue()) {
                values.add(arrayDecoder.decodeString());
            }
        }
        if (values.size() < 3 || values.size() > 5) {
            throw decoder.createDeserializationException("Expected OffsetTime array", values);
        }
        String offset = values.get(values.size() - 1);
        int numericValues = values.size() - 1;
        int hour = intValue(decoder, values.get(0));
        int minute = intValue(decoder, values.get(1));
        int second = numericValues > 2 ? intValue(decoder, values.get(2)) : 0;
        int nano = numericValues > 3 ? intValue(decoder, values.get(3)) : 0;
        if (!context.getFeatures().contains(DeserializationConfiguration.Feature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)) {
            nano *= 1_000_000;
        }
        return OffsetTime.of(LocalTime.of(hour, minute, second, nano), ZoneOffset.of(offset));
    }

    private static int intValue(Decoder decoder, String value) throws IOException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw decoder.createDeserializationException("Expected numeric OffsetTime array value", value);
        }
    }
}
