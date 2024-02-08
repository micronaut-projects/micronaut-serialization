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
import io.micronaut.serde.Encoder;
import io.micronaut.serde.support.SerdeRegistrar;

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
}
