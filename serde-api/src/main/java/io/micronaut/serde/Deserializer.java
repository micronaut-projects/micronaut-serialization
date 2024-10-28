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

import io.micronaut.core.annotation.Indexed;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.reference.PropertyReference;
import io.micronaut.serde.reference.PropertyReferenceManager;

import java.io.IOException;
import java.util.Optional;

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
     * @param context The decoder context
     * @param type The context, including any annotation metadata and type information to narrow the deserializer type
     * @return An instance of the same type of deserializer
     */
    default @NonNull Deserializer<T> createSpecific(@NonNull DecoderContext context,
                                                    @NonNull Argument<? super T> type) throws SerdeException {
        return this;
    }

    /**
     * Deserializes from the current state of the {@link Decoder} an object of type {@link T}.
     *
     * @param decoder The decoder, never {@code null}
     * @param context The decoder context, never {@code null}
     * @param type The generic type to be deserialized
     * @return The deserialized object or {@code null} only if {@link #allowNull()} returns {@code true}
     * @throws IOException If an error occurs during deserialization of the object
     */
    @Nullable
    T deserialize(
            @NonNull Decoder decoder,
            @NonNull DecoderContext context,
            @NonNull Argument<? super T> type) throws IOException;

    /**
     * Deserializes from the current state of the {@link Decoder} an object of type {@link T}. If
     * the decoder value is {@code null}, this <i>must</i> be permitted. By default, in this case,
     * this method will return {@code null}.
     *
     * @param decoder The decoder, never {@code null}
     * @param context The decoder context, never {@code null}
     * @param type The generic type to be deserialized
     * @return The deserialized object or {@code null}
     * @throws IOException If an error occurs during deserialization of the object
     * @since 2.0.0
     */
    default T deserializeNullable(
        @NonNull Decoder decoder,
        @NonNull DecoderContext context,
        @NonNull Argument<? super T> type) throws IOException {
        if (allowNull()) {
            return deserialize(decoder, context, type);
        } else {
            return decoder.decodeNull() ? null : deserialize(decoder, context, type);
        }
    }

    /**
     * Return true if the decoder can accept the null value by converting it to something else or just returning null.
     * @return Whether the deserializer is allowed to emit {@code null}
     * @deprecated Use and override {@link #deserializeNullable} instead
     */
    @Deprecated
    default boolean allowNull() {
        return false;
    }

    /**
     * Obtains a default value that can be returned from this deserializer in the case where a value is absent.
     * @return The default value
     * @param context The decoder context, never {@code null}
     * @param type The generic type to be deserialized
     */
    default @Nullable T getDefaultValue(@NonNull DecoderContext context,
                                        @NonNull Argument<? super T> type) {
        return null;
    }

    /**
     * Context object passed to the {@link #deserialize(Decoder, io.micronaut.serde.Deserializer.DecoderContext, io.micronaut.core.type.Argument)} method along with the decoder.
     */
    interface DecoderContext extends PropertyReferenceManager, DeserializerLocator, NamingStrategyLocator {

        /**
         * @return Conversion service
         */
        @NonNull
        default ConversionService getConversionService() {
            return ConversionService.SHARED;
        }

        /**
         * @param views Views to check.
         * @return {@code true} iff any of the given views is enabled.
         */
        default boolean hasView(Class<?>... views) {
            return true;
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

        /**
         * Get the {@link SerdeConfiguration} for this context.
         *
         * @return The {@link SerdeConfiguration}, or an empty optional if the default should be used
         * @since 2.7.0
         */
        @NonNull
        default Optional<SerdeConfiguration> getSerdeConfiguration() {
            return Optional.empty();
        }

        /**
         * Get the {@link DeserializationConfiguration} for this context.
         *
         * @return The {@link DeserializationConfiguration}, or an empty optional if the default should be used
         * @since 2.7.0
         */
        @NonNull
        default Optional<DeserializationConfiguration> getDeserializationConfiguration() {
            return Optional.empty();
        }
    }
}
