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
package io.micronaut.serde.serdes;

import java.io.IOException;
import java.sql.Date;
import java.time.LocalDate;

import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.util.NullableSerde;
import jakarta.inject.Singleton;

/**
 * Serde for SQL dates.
 *
 * @since 1.0.0
 */
@Singleton
@Secondary
final class SqlDateSerde implements NullableSerde<Date> {
    private static final Argument<LocalDate> LOCAL_DATE_ARGUMENT = Argument.of(LocalDate.class);
    private final LocalDateSerde localDateSerde;

    SqlDateSerde(LocalDateSerde localDateSerde) {
        this.localDateSerde = localDateSerde;
    }

    @Override
    public Deserializer<Date> createSpecific(Argument<? super Date> context, DecoderContext decoderContext)
            throws SerdeException {
        final Argument<LocalDate> argument = Argument.of(LocalDate.class, context.getAnnotationMetadata());
        final Deserializer<LocalDate> specific = localDateSerde.createSpecific(
                argument,
                decoderContext
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
    public Serializer<Date> createSpecific(Argument<? extends Date> type, EncoderContext encoderContext) {
        final Argument<LocalDate> argument = Argument.of(LocalDate.class, type.getAnnotationMetadata());
        final Serializer<LocalDate> specific = localDateSerde.createSpecific(
                argument,
                encoderContext
        );
        if (specific != localDateSerde) {
            return (encoder, context, value, t) -> specific.serialize(
                    encoder,
                    context,
                    value.toLocalDate(),
                    argument
            );
        }
        return this;
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Date value, Argument<? extends Date> type) throws IOException {
        localDateSerde.serialize(
                encoder,
                context,
                value.toLocalDate(),
                LOCAL_DATE_ARGUMENT
        );
    }

    @Override
    public Date deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super Date> type)
            throws IOException {
        final LocalDate localDate = localDateSerde.deserialize(
                decoder,
                decoderContext,
                LOCAL_DATE_ARGUMENT
        );
        if (localDate != null) {
            return Date.valueOf(localDate);
        }
        return null;
    }
}
