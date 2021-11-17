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
import io.micronaut.core.type.Argument;
import io.micronaut.serde.exceptions.SerdeException;

/**
 * Locator strategy interface for serializers.
 *
 * @since 1.0.0
 */
public interface SerializerLocator {
    /**
     * Gets a custom serializer.
     * @param serializerClass The serializer class, should not be {@code null}
     * @param <T> The generic type
     * @param <D> The serializer type
     * @return The serializer
     * @throws io.micronaut.serde.exceptions.SerdeException if no serializer is found
     */
    @NonNull
    <T, D extends Serializer<? extends T>> D findCustomSerializer(@NonNull Class<? extends D> serializerClass)
            throws SerdeException;

    /**
     * Finds a serializer for the given type.
     * @param forType The type
     * @param <T> The generic type
     * @return The serializer
     * @throws io.micronaut.serde.exceptions.SerdeException if an exception occurs
     */
    @NonNull
    <T> Serializer<? super T> findSerializer(@NonNull Argument<? extends T> forType)
            throws SerdeException;

    /**
     * Finds a serializer for the given type.
     * @param forType The type
     * @param <T> The generic type
     * @return The serializer
     * @throws io.micronaut.serde.exceptions.SerdeException if an exception occurs
     */
    default @NonNull
    <T> Serializer<? super T> findSerializer(@NonNull Class<? extends T> forType)
            throws SerdeException {
        return findSerializer(Argument.of(forType));
    }

}
