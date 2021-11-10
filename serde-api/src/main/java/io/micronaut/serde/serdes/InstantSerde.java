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
import java.time.Instant;
import java.time.temporal.TemporalQuery;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import jakarta.inject.Singleton;

/**
 * Serde used for {@link java.time.Instant}.
 *
 * @since 1.0.0
 */
@Singleton
public class InstantSerde implements TemporalSerde<Instant> {
    @Override
    public void serialize(Encoder encoder, EncoderContext context, Instant value, Argument<? extends Instant> type)
            throws IOException {
        encoder.encodeLong(value.toEpochMilli());
    }

    @Override
    public TemporalQuery<Instant> query() {
        return Instant::from;
    }

    @Override
    public Instant deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super Instant> type)
            throws IOException {
        return Instant.ofEpochMilli(decoder.decodeLong());
    }
}
