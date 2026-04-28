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
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.FormatConfiguration;
import io.micronaut.serde.FormattedSerde;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.SerdeRegistrar;
import io.micronaut.serde.support.util.SerdeFeatures;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Serde for dates.
 */
@Internal
final class DateSerde implements FormattedSerde<Date>, SerdeRegistrar<Date> {
    private static final Argument<Instant> INSTANT_ARGUMENT = Argument.of(Instant.class);
    private final InstantSerde instantSerde;

    DateSerde(InstantSerde instantSerde) {
        this.instantSerde = instantSerde;
    }

    @Override
    public Serializer<Date> createSpecific(EncoderContext encoderContext, Argument<? extends Date> type) {
        encoderContext = SerdeFeatures.withFeatures(encoderContext, type.getAnnotationMetadata());
        FormatConfiguration format = FormatConfiguration.from(type.getAnnotationMetadata());
        return format == null ? createSpecificWithoutFormat(encoderContext, type) : createSpecific(encoderContext, type, format);
    }

    @Override
    public Serializer<Date> createSpecific(EncoderContext encoderContext,
                                           Argument<? extends Date> type,
                                           @NonNull FormatConfiguration format) {
        if (isNumericShape(format)) {
            return new TimestampMillisDateSerde<>();
        }
        Optional<SimpleDateFormat> dateFormat = createDateFormat(format);
        if (dateFormat.isPresent()) {
            return new FormattedDateSerde<>(dateFormat.get());
        }
        if (format.shape() == FormatConfiguration.Shape.ARRAY) {
            final Serializer<Instant> specific = instantSerde.createSpecific(encoderContext, INSTANT_ARGUMENT);
            return createSpecificSerializer(INSTANT_ARGUMENT, specific);
        }
        final Argument<Instant> argument = Argument.of(Instant.class, type.getAnnotationMetadata());
        final Serializer<Instant> specific = instantSerde.createSpecific(encoderContext, argument, format);
        return createSpecificSerializer(argument, specific);
    }

    @Override
    public Deserializer<Date> createSpecific(DecoderContext decoderContext, Argument<? super Date> context)
            throws SerdeException {
        decoderContext = SerdeFeatures.withFeatures(decoderContext, context.getAnnotationMetadata());
        FormatConfiguration format = FormatConfiguration.from(context.getAnnotationMetadata());
        return format == null ? createSpecificWithoutFormat(decoderContext, context) : createSpecific(decoderContext, context, format);
    }

    @Override
    public Deserializer<Date> createSpecific(DecoderContext decoderContext,
                                             Argument<? super Date> context,
                                             @NonNull FormatConfiguration format) throws SerdeException {
        if (isNumericShape(format)) {
            return new TimestampMillisDateSerde<>();
        }
        Optional<SimpleDateFormat> dateFormat = createDateFormat(format);
        if (dateFormat.isPresent()) {
            return new FormattedDateSerde<>(dateFormat.get());
        }
        if (format.shape() == FormatConfiguration.Shape.ARRAY) {
            final Deserializer<Instant> specific = instantSerde.createSpecific(decoderContext, INSTANT_ARGUMENT);
            return createSpecificDeserializer(INSTANT_ARGUMENT, specific);
        }
        final Argument<Instant> argument = Argument.of(Instant.class, context.getAnnotationMetadata());
        final Deserializer<Instant> specific = instantSerde.createSpecific(decoderContext, argument, format);
        return createSpecificDeserializer(argument, specific);
    }

    private Serializer<Date> createSpecificWithoutFormat(EncoderContext encoderContext,
                                                         Argument<? extends Date> type) {
        final Argument<Instant> argument = Argument.of(Instant.class, type.getAnnotationMetadata());
        final Serializer<Instant> specific = instantSerde.createSpecific(encoderContext, argument);
        return createSpecificSerializer(argument, specific);
    }

    private Deserializer<Date> createSpecificWithoutFormat(DecoderContext decoderContext,
                                                           Argument<? super Date> context) throws SerdeException {
        final Argument<Instant> argument = Argument.of(Instant.class, context.getAnnotationMetadata());
        final Deserializer<Instant> specific = instantSerde.createSpecific(decoderContext, argument);
        return createSpecificDeserializer(argument, specific);
    }

    private Serializer<Date> createSpecificSerializer(Argument<Instant> argument,
                                                      Serializer<Instant> specific) {
        if (specific != instantSerde) {
            return new Serializer<>() {
                @Override
                public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Date> t, Date value) throws IOException {
                    specific.serialize(
                        encoder,
                        context,
                        argument, value.toInstant()
                    );
                }

                @Override
                public boolean isDefault(EncoderContext context, Date value) {
                    return value.getTime() == 0L;
                }
            };
        }
        return this;
    }

    private Deserializer<Date> createSpecificDeserializer(Argument<Instant> argument,
                                                          Deserializer<Instant> specific) {
        if (specific != instantSerde) {
            return (decoder, subContext, type) -> {
                final Instant i = specific.deserialize(
                    decoder,
                    subContext,
                    argument
                );
                if (i != null) {
                    return Date.from(i);
                }
                return null;
            };
        }
        return this;
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Date> type, Date value) throws IOException {
        instantSerde.serialize(encoder, context, INSTANT_ARGUMENT, value.toInstant());
    }

    @Override
    public Date deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Date> type)
            throws IOException {
        return Date.from(instantSerde.deserialize(
                decoder,
                decoderContext,
                INSTANT_ARGUMENT
        ));
    }

    @Override
    public boolean isDefault(EncoderContext context, Date value) {
        return value.getTime() == 0L;
    }

    @Override
    public Argument<Date> getType() {
        return Argument.of(Date.class);
    }

    private static boolean isNumericShape(@NonNull FormatConfiguration format) {
        return format.shape().isNumeric();
    }

    private static Optional<SimpleDateFormat> createDateFormat(@NonNull FormatConfiguration format) {
        return format.createDateFormat();
    }

    private static final class TimestampMillisDateSerde<T extends Date> implements Serializer<T>, Deserializer<T> {
        @Override
        public void serialize(Encoder encoder,
                              EncoderContext context,
                              Argument<? extends T> type,
                              T value) throws IOException {
            encoder.encodeLong(value.getTime());
        }

        @Override
        public T deserialize(Decoder decoder,
                             DecoderContext decoderContext,
                             Argument<? super T> type) throws IOException {
            return (T) new Date(decoder.decodeLong());
        }

        @Override
        public boolean isDefault(EncoderContext context, T value) {
            return value.getTime() == 0L;
        }
    }

    private static final class FormattedDateSerde<T extends Date> implements Serializer<T>, Deserializer<T> {
        private final SimpleDateFormat dateFormat;
        private final String pattern;

        private FormattedDateSerde(SimpleDateFormat dateFormat) {
            this.dateFormat = dateFormat;
            this.pattern = dateFormat.toPattern();
        }

        @Override
        public void serialize(Encoder encoder,
                              EncoderContext context,
                              Argument<? extends T> type,
                              T value) throws IOException {
            synchronized (dateFormat) {
                encoder.encodeString(dateFormat.format(value));
            }
        }

        @Override
        public T deserialize(Decoder decoder,
                             DecoderContext decoderContext,
                             Argument<? super T> type) throws IOException {
            String value = decoder.decodeString();
            try {
                synchronized (dateFormat) {
                    return (T) dateFormat.parse(value);
                }
            } catch (ParseException e) {
                try {
                    return (T) deserializeNumericFallback(value, decoderContext);
                } catch (NumberFormatException fallbackException) {
                    e.addSuppressed(fallbackException);
                    throw new SerdeException("Error decoding date of type " + type + " using pattern " + pattern + ": " + e.getMessage(), e);
                }
            }
        }

        private static Date deserializeNumericFallback(String value, DecoderContext decoderContext) {
            BigDecimal raw = new BigDecimal(value);
            SerdeConfiguration.NumericTimeUnit numericUnit = decoderContext.getSerdeConfiguration()
                .map(SerdeConfiguration::getNumericTimeUnit)
                .orElse(SerdeConfiguration.NumericTimeUnit.LEGACY);
            if (numericUnit == SerdeConfiguration.NumericTimeUnit.LEGACY) {
                numericUnit = SerdeConfiguration.NumericTimeUnit.MILLISECONDS;
            }
            BigDecimal seconds = switch (numericUnit) {
                case SECONDS -> raw;
                case MILLISECONDS -> raw.scaleByPowerOfTen(-3);
                case NANOSECONDS -> raw.scaleByPowerOfTen(-9);
                case LEGACY -> throw new AssertionError("Should be replaced before conversion");
            };
            seconds = seconds.setScale(9, RoundingMode.DOWN);
            return Date.from(Instant.ofEpochSecond(seconds.longValue(), seconds.remainder(BigDecimal.ONE).unscaledValue().intValueExact()));
        }
    }
}
