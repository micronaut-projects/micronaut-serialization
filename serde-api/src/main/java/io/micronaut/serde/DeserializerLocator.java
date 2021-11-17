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

import java.util.Collection;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.exceptions.SerdeException;

/**
 * Locator strategy interface for deserializers.
 *
 * @since 1.0.0
 */
public interface DeserializerLocator {
    /**
     * Gets a custom deserializer.
     * @param deserializerClass The deserializer class, should not be {@code null}
     * @param <T> The generic type
     * @param <D> The deserializer type
     * @return The deserializer
     * @throws io.micronaut.serde.exceptions.SerdeException if no deserializer is found
     */
    @NonNull
    <T, D extends Deserializer<? extends T>> D findCustomDeserializer(@NonNull Class<? extends D> deserializerClass)
            throws SerdeException;

    /**
     * Finds a deserializer for the given type.
     * @param type The type, should not be {@code null}
     * @param <T> The generic type
     * @return The deserializer
     * @throws io.micronaut.serde.exceptions.SerdeException if no deserializer is found
     */
    @NonNull <T> Deserializer<? extends T> findDeserializer(@NonNull Argument<? extends T> type)
            throws SerdeException;

    /**
     * Finds a deserializer for the given type.
     * @param type The type, should not be {@code null}
     * @param <T> The generic type
     * @return The deserializer
     * @throws io.micronaut.serde.exceptions.SerdeException if no deserializer is found
     */
    default @NonNull <T> Deserializer<? extends T> findDeserializer(@NonNull Class<? extends T> type)
            throws SerdeException {
        return findDeserializer(Argument.of(type));
    }

    /**
     * Locates desrializable subtypes for the given super type.
     * @param superType The super type
     * @param <T> The generic super type
     * @return The subtypes, never null
     */
    <T> Collection<BeanIntrospection<? extends T>> getDeserializableSubtypes(Class<T> superType);
}
