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
import java.util.OptionalLong;

@Internal
final class OptionalLongSerde implements SerdeRegistrar<OptionalLong> {

    @Override
    public void serialize(Encoder encoder,
                          EncoderContext context,
                          Argument<? extends OptionalLong> type, OptionalLong value) throws IOException {
        if (value.isPresent()) {
            encoder.encodeLong(value.getAsLong());
        } else {
            encoder.encodeNull();
        }
    }

    @Override
    public OptionalLong deserialize(Decoder decoder, DecoderContext context, Argument<? super OptionalLong> type)
        throws IOException {
        if (decoder.decodeNull()) {
            return OptionalLong.empty();
        } else {
            return OptionalLong.of(decoder.decodeLong());
        }
    }

    @Override
    public OptionalLong deserializeNullable(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super OptionalLong> type) throws IOException {
        return deserialize(decoder, context, type);
    }

    @Override
    public OptionalLong getDefaultValue(DecoderContext context, Argument<? super OptionalLong> type) {
        return OptionalLong.empty();
    }

    @Override
    public boolean isEmpty(EncoderContext context, OptionalLong value) {
        return value == null || value.isEmpty();
    }

    @Override
    public boolean isAbsent(EncoderContext context, OptionalLong value) {
        return value == null || value.isEmpty();
    }

    @Override
    public Argument<OptionalLong> getType() {
        return Argument.of(OptionalLong.class);
    }
}
