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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.support.SerdeRegistrar;

import java.io.IOException;
import java.util.Arrays;

@Internal
final class CharSerde implements SerdeRegistrar<Character> {
    @Override
    public Character deserialize(Decoder decoder,
                                 DecoderContext decoderContext,
                                 Argument<? super Character> type) throws IOException {
        return decoder.decodeChar();
    }

    @Override
    public Character deserializeNullable(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super Character> type) throws IOException {
        return decoder.decodeCharNullable();
    }

    @Override
    public void serialize(Encoder encoder,
                          EncoderContext context,
                          Argument<? extends Character> type, Character value) throws IOException {
        encoder.encodeChar(value);
    }

    @Override
    public Argument<Character> getType() {
        return Argument.of(Character.class);
    }

    @Override
    public Iterable<Argument<?>> getTypes() {
        return Arrays.asList(
            getType(), Argument.CHAR
        );
    }

    @Nullable
    @Override
    public Character getDefaultValue(@NonNull DecoderContext context, @NonNull Argument<? super Character> type) {
        return type.isPrimitive() ? (char) 0 : null;
    }
}
