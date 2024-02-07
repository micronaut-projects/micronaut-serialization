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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.support.SerdeRegistrar;

import java.io.IOException;
import java.util.Arrays;

final class BooleanSerde implements SerdeRegistrar<Boolean> {
    @Override
    public Boolean deserialize(Decoder decoder,
                               DecoderContext decoderContext,
                               Argument<? super Boolean> type) throws IOException {
        return decoder.decodeBoolean();
    }

    @Override
    public Boolean deserializeNullable(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super Boolean> type) throws IOException {
        return decoder.decodeBooleanNullable();
    }

    @Override
    public void serialize(Encoder encoder,
                          EncoderContext context,
                          Argument<? extends Boolean> type, Boolean value) throws IOException {
        encoder.encodeBoolean(value);
    }

    @Override
    public Argument<Boolean> getType() {
        return Argument.of(Boolean.class);
    }

    @Override
    public Iterable<Argument<?>> getTypes() {
        return Arrays.asList(
            getType(), Argument.BOOLEAN
        );
    }

    @Nullable
    @Override
    public Boolean getDefaultValue(@NonNull DecoderContext context, @NonNull Argument<? super Boolean> type) {
        return type.isPrimitive() ? false : null;
    }
}
