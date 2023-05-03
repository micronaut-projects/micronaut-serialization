/*
 * Copyright 2017-2022 original authors
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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;

import java.io.IOException;

/**
 * The type of deserializer that requires a specific implementation by calling {@link #createSpecific(DecoderContext, Argument)}.
 *
 * @param <T> The deserializer type
 * @author Denis Stepanov
 */
public interface CustomizableDeserializer<T> extends Deserializer<T> {

    @Override
    default T deserialize(Decoder decoder, DecoderContext context, Argument<? super T> type) throws IOException {
        throw new IllegalStateException("Specific deserializer required!");
    }

    @Override
    default T deserializeNullable(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super T> type) throws IOException {
        throw new IllegalStateException("Specific deserializer required!");
    }

    @Override
    default boolean allowNull() {
        throw new IllegalStateException("Specific deserializer required!");
    }

    @Override
    default T getDefaultValue(DecoderContext context, Argument<? super T> type) {
        throw new IllegalStateException("Specific deserializer required!");
    }
}
