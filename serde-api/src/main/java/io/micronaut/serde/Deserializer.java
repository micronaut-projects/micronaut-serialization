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

import java.io.IOException;

import io.micronaut.core.annotation.Indexed;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.beans.DeserIntrospection;
import io.micronaut.serde.exceptions.SerdeException;

/**
 * Interface that represents a build-time generated deserializer.
 *
 * @param <T> The generic type that the deserializer can deserialize
 * @author Jonas Konrad
 * @author graemerocher
 */
@Indexed(Deserializer.class)
public interface Deserializer<T> {
    /**
     * Deserializes from the current state of the {@link Decoder} an object of type {@link T}.
     *
     * @param decoder The decoder, never {@code null}
     * @param decoderContext The decoder context, never {@code null}
     * @param generics The generic type arguments for this type if any
     * @return The deserialized object or {@code null} only if {@link #allowNull()} returns {@code true}
     * @throws IOException If an error occurs during deserialization of the object
     */
    @Nullable
    T deserialize(
            @NonNull Decoder decoder,
            @NonNull DecoderContext decoderContext,
            Argument<? super T> type,
            Argument<?>... generics) throws IOException;

    /**
     * @return Whether the deserializer is allowed to emit {@code null}
     */
    default boolean allowNull() {
        return false;
    }

    /**
     * Context object passed to the {@link #deserialize(Decoder, io.micronaut.serde.Deserializer.DecoderContext, io.micronaut.core.type.Argument, io.micronaut.core.type.Argument[])} method along with the decoder.
     */
    interface DecoderContext {
        /**
         * Finds a deserializer for the given type.
         * @param type The type, should not be {@code null}
         * @param <T> The generic type
         * @return The deserializer
         * @throws io.micronaut.serde.exceptions.SerdeException if no deserializer is found
         */
        <T> Deserializer<? extends T> findDeserializer(@NonNull Argument<? extends T> type)
            throws SerdeException;

        /**
         * Finds a deserializer for the given type.
         * @param type The type, should not be {@code null}
         * @param <T> The generic type
         * @return The deserializer
         * @throws io.micronaut.serde.exceptions.SerdeException if no deserializer is found
         */
        default <T> Deserializer<? extends T> findDeserializer(@NonNull Class<? extends T> type)
                throws SerdeException {
            return findDeserializer(Argument.of(type));
        }

        /**
         * Gets an introspection for the given type for serialization.
         * @param type The type
         * @param <T> The generic type
         * @return The introspection, never {@code null}
         * @throws io.micronaut.core.beans.exceptions.IntrospectionException if no introspection exists
         */
        @NonNull <T> DeserIntrospection<T> getDeserializableIntrospection(@NonNull Argument<T> type);
    }
}
