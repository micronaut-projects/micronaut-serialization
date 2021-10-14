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

/**
 * Interface that represents a build-time generated deserializer.
 *
 * @param <T> The generic type that the deserializer can deserialize
 * @author Jonas Konrad
 * @author graemerocher
 */
public interface Deserializer<T> {
    /**
     * Deserializes from the current state of the {@link io.micronaut.json.Decoder} an object of type {@link T}.
     *
     * @param decoder The decoder, never {@code null}
     * @return The deserialized object or {@code null} only if {@link #allowNull()} returns {@code true}
     * @throws IOException If an error occurs during deserialization of the object
     */
    @Nullable
    T deserialize(@NonNull Decoder decoder) throws IOException;

    /**
     * @return Whether the deserializer is allowed to emit {@code null}
     */
    default boolean allowNull() {
        return false;
    }

    /**
     * A factory capable of constructing a {@link io.micronaut.json.Deserializer} instance.
     */
    @Indexed(Factory.class)
    interface Factory extends BaseCodecFactory {
        @NonNull
        @Override
        Type getGenericType();

        @NonNull
        @Override
        Deserializer<?> newInstance(@NonNull SerdeRegistry registry, @Nullable Function<String, Type> getTypeParameter);
    }
}
