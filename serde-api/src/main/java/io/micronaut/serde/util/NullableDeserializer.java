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

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

/**
 * Sub-interface of {@link Deserializer} for deserializers that allow {@code null}
 * through {@link #deserializeNullable(Decoder, DecoderContext, Argument)}.
 *
 * @param <T> The type to deserialize
 */
@FunctionalInterface
public interface NullableDeserializer<T> extends Deserializer<T> {
    @Override
    default T deserialize(Decoder decoder, DecoderContext context, Argument<? super T> type) throws IOException {
        return deserializeNonNull(decoder, context, type);
    }

    @Override
    default @Nullable T deserializeNullable(Decoder decoder, DecoderContext context, Argument<? super T> type) throws IOException {
        if (decoder.decodeNull()) {
            return null;
        }
        return deserializeNonNull(decoder, context, type);
    }

    /**
     * A method that is invoked when the value is known not to be null.
     *
     * @param decoder The decoder
     * @param decoderContext The decoder context
     * @param type The type
     * @return The value
     * @throws IOException if something goes wrong during deserialization
     */
    T deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super T> type) throws IOException;
}
