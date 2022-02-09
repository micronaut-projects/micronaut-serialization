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

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;

import java.io.IOException;

/**
 * The type of serializer that requires a specific implementation by calling {@link #createSpecific(Serializer.EncoderContext, Argument)}.
 *
 * @param <T> The serializer type
 * @author Denis Stepanov
 */
public interface CustomizableSerializer<T> extends Serializer<T> {

    @Override
    default void serialize(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException {
        throw new IllegalStateException("Specific serializer required!");
    }

    @Override
    default boolean isEmpty(EncoderContext context, @Nullable T value) {
        throw new IllegalStateException("Specific serializer required!");
    }

    @Override
    default boolean isAbsent(EncoderContext context, @Nullable T value) {
        throw new IllegalStateException("Specific serializer required!");
    }
}
