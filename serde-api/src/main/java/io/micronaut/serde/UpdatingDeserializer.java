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
package io.micronaut.serde;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import java.io.IOException;

/**
 *
 * @author graemerocher
 */
public interface UpdatingDeserializer<T> extends Deserializer<T> {

    /**
     * Deserializes from the current state of the {@link Decoder} an object of type {@link T}.
     *
     * @param decoder The decoder, never {@code null}
     * @param decoderContext The decoder context, never {@code null}
     * @param type The generic type to be deserialized
     * @param value The value
     * @return The deserialized object or {@code null} only if {@link #allowNull()} returns {@code true}
     * @throws IOException If an error occurs during deserialization of the object
     */
    @Nullable
    void deserializeInto(
            @NonNull Decoder decoder,
            @NonNull DecoderContext decoderContext,
            @NonNull Argument<? super T> type,
            T value) throws IOException;
}
