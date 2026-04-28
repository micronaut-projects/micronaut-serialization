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
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Serde for SQL dates.
 *
 * @since 1.0.0
 */
final class SqlDateSerde implements FormattedSerde<Date>, SerdeRegistrar<Date> {
    private static final Argument<Instant> INSTANT_ARGUMENT = Argument.of(Instant.class);
    private final LocalDateSerde localDateSerde;
    private final InstantSerde instantSerde;

    SqlDateSerde(LocalDateSerde localDateSerde, InstantSerde instantSerde) {
        this.localDateSerde = localDateSerde;
        this.instantSerde = instantSerde;
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
        if (format.shape().isNumeric()) {
            return new TimestampMillisDateSerde();
        }
        if (format.pattern() == null) {
            if (format.shape() == FormatConfiguration.Shape.ARRAY) {
                final Deserializer<Instant> specific = instantSerde.createSpecific(decoderContext, INSTANT_ARGUMENT);
                return createInstantDeserializer(INSTANT_ARGUMENT, specific);
            }
            final Argument<Instant> argument = Argument.of(Instant.class, context.getAnnotationMetadata());
            final Deserializer<Instant> specific = instantSerde.createSpecific(decoderContext, argument, format);
            return createInstantDeserializer(argument, specific);
        }
        final Argument<LocalDate> argument = Argument.of(LocalDate.class, context.getAnnotationMetadata());
        final Deserializer<LocalDate> specific = localDateSerde.createSpecific(
            decoderContext,
            argument,
            format
        );
        if (specific != localDateSerde) {
            return (decoder, subContext, type) -> {
                final LocalDate ld = specific.deserialize(
                    decoder,
                    subContext,
                    argument
                );
                if (ld != null) {
                    return Date.valueOf(ld);
                }
                return null;
            };
        }
        return this;
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
        if (format.shape().isNumeric()) {
            return new TimestampMillisDateSerde();
        }
        if (format.pattern() == null) {
            if (format.shape() == FormatConfiguration.Shape.ARRAY) {
                final Serializer<Instant> specific = instantSerde.createSpecific(encoderContext, INSTANT_ARGUMENT);
                return createInstantSerializer(INSTANT_ARGUMENT, specific);
            }
            final Argument<Instant> argument = Argument.of(Instant.class, type.getAnnotationMetadata());
            final Serializer<Instant> specific = instantSerde.createSpecific(encoderContext, argument, format);
            return createInstantSerializer(argument, specific);
        }
        final Argument<LocalDate> argument = Argument.of(LocalDate.class, type.getAnnotationMetadata());
        final Serializer<LocalDate> specific = localDateSerde.createSpecific(
            encoderContext,
            argument,
            format
        );
        if (specific != localDateSerde) {
            return new Serializer<>() {
                @Override
                public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Date> t, Date value) throws IOException {
                    specific.serialize(
                        encoder,
                        context,
                        argument, value.toLocalDate()
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

    private Deserializer<Date> createSpecificWithoutFormat(DecoderContext decoderContext,
                                                           Argument<? super Date> context) throws SerdeException {
        final Argument<Instant> argument = Argument.of(Instant.class, context.getAnnotationMetadata());
        final Deserializer<Instant> specific = instantSerde.createSpecific(decoderContext, argument);
        return createInstantDeserializer(argument, specific);
    }

    private Serializer<Date> createSpecificWithoutFormat(EncoderContext encoderContext,
                                                         Argument<? extends Date> type) {
        final Argument<Instant> argument = Argument.of(Instant.class, type.getAnnotationMetadata());
        final Serializer<Instant> specific = instantSerde.createSpecific(encoderContext, argument);
        return createInstantSerializer(argument, specific);
    }

    private static Deserializer<Date> createInstantDeserializer(Argument<Instant> argument,
                                                                Deserializer<Instant> specific) {
        return (decoder, subContext, type) -> {
            final Instant instant = specific.deserialize(
                decoder,
                subContext,
                argument
            );
            if (instant != null) {
                return new Date(instant.toEpochMilli());
            }
            return null;
        };
    }

    private static Serializer<Date> createInstantSerializer(Argument<Instant> argument,
                                                            Serializer<Instant> specific) {
        return new Serializer<>() {
            @Override
            public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Date> t, Date value) throws IOException {
                specific.serialize(
                    encoder,
                    context,
                    argument, Instant.ofEpochMilli(value.getTime())
                );
            }

            @Override
            public boolean isDefault(EncoderContext context, Date value) {
                return value.getTime() == 0L;
            }
        };
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Date> type, Date value) throws IOException {
        instantSerde.serialize(
            encoder,
            context,
            Argument.of(Instant.class), Instant.ofEpochMilli(value.getTime())
        );
    }

    @Override
    public Date deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Date> type)
        throws IOException {
        final Instant instant = instantSerde.deserialize(
            decoder,
            decoderContext,
            Argument.of(Instant.class)
        );
        if (instant != null) {
            return new Date(instant.toEpochMilli());
        }
        return null;
    }

    @Override
    public boolean isDefault(EncoderContext context, Date value) {
        return value.getTime() == 0L;
    }

    @Override
    public Argument<Date> getType() {
        return Argument.of(Date.class);
    }

    private static final class TimestampMillisDateSerde implements Serializer<Date>, Deserializer<Date> {
        @Override
        public void serialize(Encoder encoder,
                              EncoderContext context,
                              Argument<? extends Date> type,
                              Date value) throws IOException {
            encoder.encodeLong(value.getTime());
        }

        @Override
        public Date deserialize(Decoder decoder,
                                DecoderContext decoderContext,
                                Argument<? super Date> type) throws IOException {
            return new Date(decoder.decodeLong());
        }

        @Override
        public boolean isDefault(EncoderContext context, Date value) {
            return value.getTime() == 0L;
        }
    }
}
