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
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.support.SerdeRegistrar;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

@Internal
final class NumberTypeSerde implements SerdeRegistrar<Number>, NumberSerde<Number> {

    @Override
    public Argument<Number> getType() {
        return Argument.of(Number.class);
    }

    @Override
    public Number deserialize(Decoder decoder,
                              DecoderContext decoderContext,
                              Argument<? super Number> type) throws IOException {
        return decoder.decodeNumber();
    }

    @Override
    public @Nullable Number deserializeNullable(Decoder decoder, DecoderContext context, Argument<? super Number> type) throws IOException {
        return decoder.decodeNumberNullable();
    }

    @Override
    public void serialize(Encoder encoder,
                          EncoderContext context,
                          Argument<? extends Number> type,
                          Number value) throws IOException {
        encodeNumber(encoder, value);
    }

    @Nullable
    @Override
    public Integer getDefaultValue(DecoderContext context, Argument<? super Number> type) {
        return type.isPrimitive() ? 0 : null;
    }
}
