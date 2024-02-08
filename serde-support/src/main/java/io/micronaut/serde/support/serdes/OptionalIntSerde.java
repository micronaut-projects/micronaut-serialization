/*
 * Copyright 2017-2024 original authors
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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.support.SerdeRegistrar;

import java.io.IOException;
import java.util.OptionalInt;

@Internal
final class OptionalIntSerde implements SerdeRegistrar<OptionalInt> {

    @Override
    public void serialize(Encoder encoder,
                          EncoderContext context,
                          Argument<? extends OptionalInt> type, OptionalInt value) throws IOException {
        if (value.isPresent()) {
            encoder.encodeInt(value.getAsInt());
        } else {
            encoder.encodeNull();
        }
    }

    @Override
    public OptionalInt deserialize(Decoder decoder, DecoderContext context, Argument<? super OptionalInt> type)
        throws IOException {
        if (decoder.decodeNull()) {
            return OptionalInt.empty();
        } else {
            return OptionalInt.of(
                decoder.decodeInt()
            );
        }
    }

    @Override
    public OptionalInt deserializeNullable(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super OptionalInt> type) throws IOException {
        return deserialize(decoder, context, type);
    }

    @Override
    public OptionalInt getDefaultValue(DecoderContext context, Argument<? super OptionalInt> type) {
        return OptionalInt.empty();
    }

    @Override
    public boolean isEmpty(EncoderContext context, OptionalInt value) {
        return value == null || value.isEmpty();
    }

    @Override
    public boolean isAbsent(EncoderContext context, OptionalInt value) {
        return value == null || value.isEmpty();
    }

    @Override
    public Argument<OptionalInt> getType() {
        return Argument.of(OptionalInt.class);
    }
}
