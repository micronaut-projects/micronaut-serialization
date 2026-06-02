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
import java.util.Optional;
import java.util.stream.Collectors;

import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.annotation.Serdeable;
import org.jspecify.annotations.Nullable;

/**
 * Interface that abstracts the lookup for introspections usable for serialization and/or deserialization.
 */
public interface SerdeIntrospections {
    /**
     * The bean introspector to use.
     * @return The introspector
     */
    default BeanIntrospector getBeanIntrospector() {
        return BeanIntrospector.SHARED;
    }

    /**
     * Gets an introspection for the given type for serialization.
     * @param type The type
     * @param <T> The generic type
     * @return The introspection, never {@code null}
     * @throws io.micronaut.core.beans.exceptions.IntrospectionException if no introspection exists
     */
    <T> BeanIntrospection<T> getSerializableIntrospection(Argument<T> type);

    /**
     * Gets an introspection for the given type for deserialization.
     * @param type The type
     * @param <T> The generic type
     * @return The introspection, never {@code null}
     * @throws io.micronaut.core.beans.exceptions.IntrospectionException if no introspection exists
     */
    <T> BeanIntrospection<T> getDeserializableIntrospection(Argument<T> type);

    /**
     * Creates introspections that first consult the given runtime resolver.
     *
     * @param resolver The runtime introspection resolver
     * @return The resolver-backed introspections
     * @since 3.0.1
     */
    default SerdeIntrospections withRuntimeIntrospectionResolver(RuntimeIntrospectionResolver resolver) {
        return new RuntimeResolverSerdeIntrospections(this, resolver);
    }

    /**
     * Gets an subtype introspection for the given type for deserialization.
     * @param type The type
     * @param <T> The generic type
     * @return A collection of introspections, never {@code null}
     */
    default <T> Collection<BeanIntrospection<? extends T>> findSubtypeDeserializables(Class<T> type) {
        final List list =
                getBeanIntrospector().findIntrospections(ref -> {
                    if (ref.isPresent()) {
                        final Class<?> bt = ref.getBeanType();
                        return bt != type && type.isAssignableFrom(bt);
                    }
                    return false;
                }).stream().filter(bi -> bi.hasStereotype(Serdeable.Deserializable.class))
                .collect(Collectors.toList());
        return list;
    }

    /**
     * The kind of runtime introspection being requested.
     *
     * @since 3.0.1
     */
    enum RuntimeIntrospectionKind {
        /**
         * Runtime introspection for serialization.
         */
        SERIALIZATION,

        /**
         * Runtime introspection for deserialization.
         */
        DESERIALIZATION
    }

    /**
     * A runtime introspection request.
     *
     * @param argument The requested argument
     * @param kind The requested introspection kind
     * @param <T> The bean type
     * @since 3.0.1
     */
    record RuntimeIntrospectionRequest<T>(
        Argument<T> argument,
        RuntimeIntrospectionKind kind
    ) {
    }

    /**
     * Resolves runtime-built introspections.
     *
     * @since 3.0.1
     */
    interface RuntimeIntrospectionResolver {

        /**
         * @return A cache key that identifies this resolver configuration
         */
        @Nullable
        Object cacheKey();

        /**
         * Resolves a runtime introspection for the given request.
         *
         * @param request The request
         * @param <T> The bean type
         * @return The runtime introspection if this resolver handles the request
         */
        <T> Optional<BeanIntrospection<T>> resolve(RuntimeIntrospectionRequest<T> request);
    }
}
