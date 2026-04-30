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
import io.micronaut.serde.FormatConfiguration.Shape;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.SerdeRegistrar;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.time.Year;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalQuery;

/**
 * Serde for year.
 *
 * @since 1.0.0
 */
public class YearSerde implements TemporalSerde<Year>, SerdeRegistrar<Year> {
    @Override
    public Serializer<Year> createSpecific(EncoderContext context,
                                           Argument<? extends Year> type,
                                           FormatConfiguration format) {
        Serializer<Year> specific = TemporalSerde.super.createSpecific(context, type, format);
        if (specific != this || format.shape() != Shape.STRING) {
            return specific;
        }
        return new StringShapeSerializer(specific);
    }

    @Override
    public Deserializer<Year> createSpecific(DecoderContext context,
                                             Argument<? super Year> type,
                                             FormatConfiguration format) throws SerdeException {
        Deserializer<Year> specific = TemporalSerde.super.createSpecific(context, type, format);
        if (specific != this || format.shape() != Shape.STRING) {
            return specific;
        }
        return (decoder, decoderContext, argument) -> Year.parse(decoder.decodeString());
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Year> type, Year value) throws IOException {
        encoder.encodeInt(value.getValue());
    }

    @Override
    public TemporalQuery<Year> query() {
        return temporal -> Year.of(temporal.get(ChronoField.YEAR));
    }

    @Override
    public Year deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Year> type)
            throws IOException {
        return Year.of(decoder.decodeInt());
    }

    @Override
    public Argument<Year> getType() {
        return Argument.of(Year.class);
    }

    private static final class StringShapeSerializer implements Serializer<Year> {
        private final Serializer<Year> delegate;

        private StringShapeSerializer(Serializer<Year> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void serialize(Encoder encoder,
                              EncoderContext context,
                              Argument<? extends Year> type,
                              Year value) throws IOException {
            encoder.encodeString(value.toString());
        }

        @Override
        public boolean isEmpty(EncoderContext context, @Nullable Year value) {
            return delegate.isEmpty(context, value);
        }

        @Override
        public boolean isAbsent(EncoderContext context, @Nullable Year value) {
            return delegate.isAbsent(context, value);
        }

        @Override
        public boolean isDefault(EncoderContext context, Year value) {
            return delegate.isDefault(context, value);
        }
    }
}
