/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.serde.oracle.jdbc.json.serde;

import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.util.NullableSerde;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * The custom serde for {@link Instant} for Oracle JSON.
 *
 * @author radovanradic
 * @since 2.0.0
 */
@Singleton
@Secondary
public class OracleJsonInstantSerde implements NullableSerde<Instant> {

    @Override
    public Instant deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super Instant> type) throws IOException {
        String dateStr = decoder.decodeString();
        return LocalDateTime.parse(dateStr).atZone(ZoneId.systemDefault()).toInstant();
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Instant> type, Instant value) throws IOException {
        if (value == null) {
            encoder.encodeNull();
        } else {
            encoder.encodeString(LocalDateTime.ofInstant(value, ZoneId.systemDefault()).toString());
        }
    }
}
