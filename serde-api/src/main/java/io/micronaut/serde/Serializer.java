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
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;

/**
 * Models a build time serializer. That is a class computed at build-time that can
 * be used to serialize an instance of {@link T}.
 *
 * @param <T> The type to be serialized
 * @author Jonas Konrad
 * @author graemerocher
 */
@Indexed(Serializer.class)
public interface Serializer<T> {

    /**
     * Create a more specific serializer for the given definition.
     * @param encoderContext The encoder context
     * @param type The type definition including any annotation metadata
     * @return The more specific serializer
     */
    default @NonNull
    Serializer<T> createSpecific(@NonNull Argument<? extends T> type, @NonNull EncoderContext encoderContext) {
        return this;
    }

    /**
     * Serializes the given value using the passed {@link Encoder}.
     * @param encoder The encoder to use
     * @param context The encoder context, never {@code null}
     * @param value The value, can be {@code null}
     * @param type Models the generic type of the value
     * @throws IOException If an error occurs during serialization
     */
    void serialize(
            @NonNull Encoder encoder,
            @NonNull EncoderContext context,
            @NonNull T value,
            @NonNull Argument<? extends T> type) throws IOException;

    /**
     * Used for {@code JsonInclude.Include#NON_EMPTY} checking.
     * @param value The check to check
     * @return Return {@code true} if the value is empty
     */
    default boolean isEmpty(@Nullable T value) {
        return value == null;
    }

    /**
     * Used for {@code JsonInclude.Include#NON_ABSENT} checking.
     * @param value The value to check
     * @return Return {@code true} if the value is absent
     */
    default boolean isAbsent(T value) {
        return value == null;
    }

    /**
     * Context object passes to the
     * {@link #serialize(Encoder, io.micronaut.serde.Serializer.EncoderContext, Object, io.micronaut.core.type.Argument)}  method.
     */
    interface EncoderContext extends SerializerLocator {

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
    }
}
