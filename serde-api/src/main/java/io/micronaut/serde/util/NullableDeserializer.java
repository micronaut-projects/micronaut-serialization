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
package io.micronaut.serde.util;

import java.io.IOException;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;

/**
 * Sub-interface of {@link io.micronaut.serde.Deserializer} for deserializers that allow
 * {@code null}. Deals with the decoding of {@code null} and delegates to {@link #deserializeNonNull(io.micronaut.serde.Decoder, io.micronaut.serde.Deserializer.DecoderContext, io.micronaut.core.type.Argument)}.
 *
 * @param <T> The type to deserialize
 */
@FunctionalInterface
public interface NullableDeserializer<T> extends Deserializer<T> {
    @Override
    default T deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super T> type) throws IOException {
        if (decoder.decodeNull()) {
            return null;
        } else {
            return deserializeNonNull(decoder, decoderContext, type);
        }
    }

    /**
     * A method that is invoked when the value is known not to be null.
     * @param decoder The decoder
     * @param decoderContext The decoder context
     * @param type The type
     * @return The value
     * @throws IOException if something goes wrong during deserialization
     */
    @NonNull
    T deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super T> type) throws IOException;

    @Override
    default boolean allowNull() {
        return true;
    }
}
