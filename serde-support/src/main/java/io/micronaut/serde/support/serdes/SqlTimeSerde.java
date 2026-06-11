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
import io.micronaut.serde.Encoder;
import io.micronaut.serde.support.SerdeRegistrar;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.sql.Time;

/**
 * Serde mapping for {@link Time}.
 */
@Internal
final class SqlTimeSerde implements SerdeRegistrar<Time> {
    private static final Argument<Time> ARGUMENT = Argument.of(Time.class);

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Time> type, Time value) throws IOException {
        encoder.encodeString(value.toString());
    }

    @Override
    public Time deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Time> type) throws IOException {
        return Time.valueOf(decoder.decodeString());
    }

    @Override
    public @Nullable Time deserializeNullable(Decoder decoder, DecoderContext context, Argument<? super Time> type) throws IOException {
        if (decoder.decodeNull()) {
            return null;
        }
        return deserialize(decoder, context, type);
    }

    @Override
    public Argument<Time> getType() {
        return ARGUMENT;
    }
}
