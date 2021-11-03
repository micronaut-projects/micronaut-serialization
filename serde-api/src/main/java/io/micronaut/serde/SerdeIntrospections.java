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
import java.util.List;
import java.util.stream.Collectors;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.annotation.Serdeable;

public interface SerdeIntrospections {
    /**
     * Gets an introspection for the given type for serialization.
     * @param type The type
     * @param <T> The generic type
     * @return The introspection, never {@code null}
     * @throws io.micronaut.core.beans.exceptions.IntrospectionException if no introspection exists
     */
    @NonNull
    <T> BeanIntrospection<T> getSerializableIntrospection(@NonNull Argument<T> type);

    /**
     * Gets an introspection for the given type for deserialization.
     * @param type The type
     * @param <T> The generic type
     * @return The introspection, never {@code null}
     * @throws io.micronaut.core.beans.exceptions.IntrospectionException if no introspection exists
     */
    @NonNull <T> BeanIntrospection<T> getDeserializableIntrospection(@NonNull Argument<T> type);

    /**
     * Gets an subtype introspection for the given type for deserialization.
     * @param type The type
     * @param <T> The generic type
     * @return A collectino of introspections, never {@code null}
     */
    default @NonNull <T> Collection<BeanIntrospection<? extends T>> findSubtypeDeserializables(@NonNull Class<T> type) {
        final List list =
                BeanIntrospector.SHARED.findIntrospections(ref -> {
                    if (ref.isPresent()) {
                        final Class<?> bt = ref.getBeanType();
                        return bt != type && type.isAssignableFrom(bt);
                    }
                    return false;
                }).stream().filter(bi -> bi.hasStereotype(Serdeable.Deserializable.class))
                .collect(Collectors.toList());
        return list;
    }
}
