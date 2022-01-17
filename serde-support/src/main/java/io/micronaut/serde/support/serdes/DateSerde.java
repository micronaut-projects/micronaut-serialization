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
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.util.NullableSerde;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;

/**
 * Serde for dates.
 */
@Singleton
final class DateSerde implements NullableSerde<Date> {
    private static final Argument<Instant> INSTANT_ARGUMENT = Argument.of(Instant.class);
    private final InstantSerde instantSerde;

    DateSerde(InstantSerde instantSerde) {
        this.instantSerde = instantSerde;
    }

    @Override
    public Serializer<Date> createSpecific(EncoderContext encoderContext, Argument<? extends Date> type) {
        final Argument<Instant> argument = Argument.of(Instant.class, type.getAnnotationMetadata());
        final Serializer<Instant> specific = instantSerde.createSpecific(
                encoderContext, argument
        );
        if (specific != instantSerde) {
            return (encoder, context, t, value) -> specific.serialize(
                    encoder,
                    context,
                    argument, value.toInstant()
            );
        }
        return this;
    }

    @Override
    public Deserializer<Date> createSpecific(DecoderContext decoderContext, Argument<? super Date> context)
            throws SerdeException {
        final Argument<Instant> argument = Argument.of(Instant.class, context.getAnnotationMetadata());
        final Deserializer<Instant> specific = instantSerde.createSpecific(decoderContext, argument);
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
    public Date deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super Date> type)
            throws IOException {
        return Date.from(instantSerde.deserializeNonNull(
                decoder,
                decoderContext,
                INSTANT_ARGUMENT
        ));
    }
}
