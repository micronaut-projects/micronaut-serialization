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
package io.micronaut.json;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.function.Function;

import io.micronaut.core.annotation.Indexed;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;

/**
 * Models a build time serializer. That is a class computed at build-time that can
 * be used to serialize an instance of {@link T}.
 *
 * @param <T> The type to be serialized
 * @author Jonas Konrad
 * @author graemerocher
 */
public interface Serializer<T> {

    /**
     * Serializes the given value using the passed {@link io.micronaut.json.Encoder}.
     * @param encoder The encoder to use
     * @param value The value, can be {@code null}
     * @throws IOException If an error occurs during serialization
     */
    void serialize(
            @NonNull Encoder encoder,
            @Nullable T value) throws IOException;

    /**
     * Used for {@code JsonInclude.Include#NON_EMPTY} checking.
     */
    default boolean isEmpty(@Nullable T value) {
        return false;
    }

    /**
     * Factory for creating an instance of a {@link io.micronaut.json.Serializer} for the given type parameters.
     */
    @Indexed(Factory.class)
    interface Factory extends BaseCodecFactory {
        /**
         * The generic type of the serializer, declared as a {@link io.micronaut.core.type.Argument}.
         * @return The generic type, never {@code null}
         */
        @Override
        @NonNull
        Argument<?> getGenericType();

        /**
         * Constructs a new serializer instance.
         * @param locator The serializer locator, never {@code null}
         * @param getTypeParameter Any additional type parameters that can be filled.
         * @return The serializer
         */
        @NonNull
        @Override
        Serializer<?> newInstance(
                @NonNull SerdeRegistry locator,
                @Nullable Function<String, Type> getTypeParameter
        );
    }
}
