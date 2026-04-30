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
import java.sql.Timestamp;
import java.time.Instant;

/**
 * Serde for SQL timestamps.
 */
final class SqlTimestampSerde implements FormattedSerde<Timestamp>, SerdeRegistrar<Timestamp> {
    private static final Argument<Instant> INSTANT_ARGUMENT = Argument.of(Instant.class);
    private final InstantSerde instantSerde;

    SqlTimestampSerde(InstantSerde instantSerde) {
        this.instantSerde = instantSerde;
    }

    @Override
    public Serializer<Timestamp> createSpecific(EncoderContext encoderContext, Argument<? extends Timestamp> type) {
        encoderContext = SerdeFeatures.withFeatures(encoderContext, type.getAnnotationMetadata());
        FormatConfiguration format = FormatConfiguration.from(type.getAnnotationMetadata());
        return format == null ? createSpecificWithoutFormat(encoderContext, type) : createSpecific(encoderContext, type, format);
    }

    @Override
    public Serializer<Timestamp> createSpecific(EncoderContext encoderContext,
                                                Argument<? extends Timestamp> type,
                                                @NonNull FormatConfiguration format) {
        if (format.shape().isNumeric()) {
            return new TimestampMillisSerde();
        }
        if (format.shape() == FormatConfiguration.Shape.ARRAY) {
            final Serializer<Instant> specific = instantSerde.createSpecific(encoderContext, INSTANT_ARGUMENT);
            return createSpecificSerializer(INSTANT_ARGUMENT, specific);
        }
        final Argument<Instant> argument = Argument.of(Instant.class, type.getAnnotationMetadata());
        final Serializer<Instant> specific = instantSerde.createSpecific(encoderContext, argument, format);
        return createSpecificSerializer(argument, specific);
    }

    private Serializer<Timestamp> createSpecificWithoutFormat(EncoderContext encoderContext,
                                                              Argument<? extends Timestamp> type) {
        final Argument<Instant> argument = Argument.of(Instant.class, type.getAnnotationMetadata());
        final Serializer<Instant> specific = instantSerde.createSpecific(encoderContext, argument);
        return createSpecificSerializer(argument, specific);
    }

    private Serializer<Timestamp> createSpecificSerializer(Argument<Instant> argument,
                                                           Serializer<Instant> specific) {
        if (specific != instantSerde) {
            return new Serializer<>() {
                @Override
                public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Timestamp> t, Timestamp value) throws IOException {
                    specific.serialize(
                        encoder,
                        context,
                        argument, value.toInstant()
                    );
                }

                @Override
                public boolean isDefault(EncoderContext context, Timestamp value) {
                    return value.getTime() == 0L;
                }
            };
        }
        return this;
    }

    @Override
    public Deserializer<Timestamp> createSpecific(DecoderContext decoderContext, Argument<? super Timestamp> context)
            throws SerdeException {
        decoderContext = SerdeFeatures.withFeatures(decoderContext, context.getAnnotationMetadata());
        FormatConfiguration format = FormatConfiguration.from(context.getAnnotationMetadata());
        return format == null ? createSpecificWithoutFormat(decoderContext, context) : createSpecific(decoderContext, context, format);
    }

    @Override
    public Deserializer<Timestamp> createSpecific(DecoderContext decoderContext,
                                                  Argument<? super Timestamp> context,
                                                  @NonNull FormatConfiguration format) throws SerdeException {
        if (format.shape().isNumeric()) {
            return new TimestampMillisSerde();
        }
        if (format.shape() == FormatConfiguration.Shape.ARRAY) {
            final Deserializer<Instant> specific = instantSerde.createSpecific(decoderContext, INSTANT_ARGUMENT);
            return createSpecificDeserializer(INSTANT_ARGUMENT, specific);
        }
        final Argument<Instant> argument = Argument.of(Instant.class, context.getAnnotationMetadata());
        final Deserializer<Instant> specific = instantSerde.createSpecific(decoderContext, argument, format);
        return createSpecificDeserializer(argument, specific);
    }

    private Deserializer<Timestamp> createSpecificWithoutFormat(DecoderContext decoderContext,
                                                                Argument<? super Timestamp> context) throws SerdeException {
        final Argument<Instant> argument = Argument.of(Instant.class, context.getAnnotationMetadata());
        final Deserializer<Instant> specific = instantSerde.createSpecific(decoderContext, argument);
        return createSpecificDeserializer(argument, specific);
    }

    private Deserializer<Timestamp> createSpecificDeserializer(Argument<Instant> argument,
                                                               Deserializer<Instant> specific) {
        if (specific != instantSerde) {
            return (decoder, subContext, type) -> {
                final Instant i = specific.deserialize(
                    decoder,
                    subContext,
                    argument
                );
                if (i != null) {
                    return Timestamp.from(i);
                }
                return null;
            };
        }
        return this;
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Timestamp> type, Timestamp value) throws IOException {
        instantSerde.serialize(encoder, context, INSTANT_ARGUMENT, value.toInstant());
    }

    @Override
    public Timestamp deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Timestamp> type) throws IOException {
        return Timestamp.from(instantSerde.deserialize(
                decoder,
                decoderContext,
                INSTANT_ARGUMENT
        ));
    }

    @Override
    public boolean isDefault(EncoderContext context, Timestamp value) {
        return value.getTime() == 0L;
    }

    @Override
    public Argument<Timestamp> getType() {
        return Argument.of(Timestamp.class);
    }

    private static final class TimestampMillisSerde implements Serializer<Timestamp>, Deserializer<Timestamp> {
        @Override
        public void serialize(Encoder encoder,
                              EncoderContext context,
                              Argument<? extends Timestamp> type,
                              Timestamp value) throws IOException {
            encoder.encodeLong(value.getTime());
        }

        @Override
        public Timestamp deserialize(Decoder decoder,
                                     DecoderContext decoderContext,
                                     Argument<? super Timestamp> type) throws IOException {
            return new Timestamp(decoder.decodeLong());
        }

        @Override
        public boolean isDefault(EncoderContext context, Timestamp value) {
            return value.getTime() == 0L;
        }
    }
}
