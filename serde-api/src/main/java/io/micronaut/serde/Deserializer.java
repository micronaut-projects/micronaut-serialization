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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.reference.PropertyReference;
import io.micronaut.serde.reference.PropertyReferenceManager;

/**
 * Interface that represents a deserializer.
 *
 * @param <T> The generic type that the deserializer can deserialize
 * @author Jonas Konrad
 * @author graemerocher
 */
@Indexed(Deserializer.class)
public interface Deserializer<T> {

    /**
     * Create a new child deserializer or return this if non is necessary for the given context.
     *
     * @param context The context, including any annotation metadata and type information to narrow the deserializer type
     * @param decoderContext The decoder context
     * @return An instance of the same type of deserializer
     */
    default @NonNull Deserializer<T> createSpecific(
            @NonNull Argument<? super T> context,
            @NonNull DecoderContext decoderContext) throws SerdeException {
        return this;
    }

    /**
     * Deserializes from the current state of the {@link Decoder} an object of type {@link T}.
     *
     * @param decoder The decoder, never {@code null}
     * @param decoderContext The decoder context, never {@code null}
     * @param type The generic type to be deserialized
     * @return The deserialized object or {@code null} only if {@link #allowNull()} returns {@code true}
     * @throws IOException If an error occurs during deserialization of the object
     */
    @Nullable
    T deserialize(
            @NonNull Decoder decoder,
            @NonNull DecoderContext decoderContext,
            @NonNull Argument<? super T> type) throws IOException;

    /**
     * @return Whether the deserializer is allowed to emit {@code null}
     */
    default boolean allowNull() {
        return false;
    }

    /**
     * Obtains a default value that can be returned from this deserializer in the case where a value is absent.
     * @return The default value
     */
    default @Nullable T getDefaultValue() {
        return null;
    }

    /**
     * Context object passed to the {@link #deserialize(Decoder, io.micronaut.serde.Deserializer.DecoderContext, io.micronaut.core.type.Argument)} method along with the decoder.
     */
    interface DecoderContext extends PropertyReferenceManager, DeserializerLocator {

        /**
         * @return Conversion service
         */
        @NonNull
        default ConversionService<?> getConversionService() {
            return ConversionService.SHARED;
        }

        /**
         * @param views Views to check.
         * @return {@code true} iff any of the given views is enabled.
         */
        default boolean hasView(Class<?>... views) {
            return false;
        }


        /**
         * Resolve a reference for the given type and value.
         * @param reference The reference
         * @param <B> The bean type
         * @param <P> The generic type of the value
         * @return The existing reference, a new one or {@code null} if serialization should be skipped
         */
        @Internal
        @Nullable
        <B, P> PropertyReference<B, P> resolveReference(
                @NonNull PropertyReference<B, P> reference
        );
    }
}
