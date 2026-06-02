/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.serde.jsonb;

import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.serde.SerdeIntrospections;
import jakarta.json.JsonValue;
import jakarta.json.bind.config.PropertyVisibilityStrategy;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Supplies JSON-B runtime introspections for classes that do not already have
 * Micronaut-generated introspection metadata.
 * <p>
 * The resolver intentionally returns empty for generated-introspection classes
 * so normal Micronaut Serialization lookup remains the default. It still caches
 * runtime introspection models for those classes because the JSON-B reflection
 * provider uses the same model to decide whether generated serialization can be
 * used without touching the caller's output stream.
 */
final class JsonbRuntimeIntrospectionResolver implements SerdeIntrospections.RuntimeIntrospectionResolver {
    private final @Nullable Object namingStrategy;
    private final String propertyOrderStrategy;
    private final @Nullable PropertyVisibilityStrategy visibilityStrategy;
    private final JsonbRuntimeCustomizations customizations;
    private final boolean includeGeneratedIntrospections;
    private final Object cacheKey;
    private final ConcurrentMap<Class<?>, JsonbRuntimeBeanIntrospection<?>> introspections = new ConcurrentHashMap<>();
    private final ConcurrentMap<Class<?>, JsonbRuntimeBeanIntrospection<?>> scopedIntrospections = new ConcurrentHashMap<>();

    /**
     * Creates a JSON-B runtime introspection resolver for one mapper
     * configuration. The {@code includeGeneratedIntrospections} flag is used
     * only by the reflection provider after JSON-B fallback rules have already
     * selected fallback; normal mapper lookup keeps generated introspections as
     * the preferred path.
     *
     * @param namingStrategy The configured JSON-B naming strategy
     * @param propertyOrderStrategy The configured JSON-B property order strategy
     * @param visibilityStrategy The configured JSON-B visibility strategy
     * @param customizations Configured JSON-B adapters, serializers, and deserializers
     * @param includeGeneratedIntrospections Whether generated-introspection classes may get runtime models
     */
    JsonbRuntimeIntrospectionResolver(@Nullable Object namingStrategy,
                                      String propertyOrderStrategy,
                                      @Nullable PropertyVisibilityStrategy visibilityStrategy,
                                      JsonbRuntimeCustomizations customizations,
                                      boolean includeGeneratedIntrospections) {
        this.namingStrategy = namingStrategy;
        this.propertyOrderStrategy = propertyOrderStrategy;
        this.visibilityStrategy = visibilityStrategy;
        this.customizations = customizations;
        this.includeGeneratedIntrospections = includeGeneratedIntrospections;
        this.cacheKey = List.of(
            namingStrategy == null ? "" : namingStrategy.getClass().getName() + ':' + namingStrategy,
            propertyOrderStrategy,
            visibilityStrategy == null ? "" : visibilityStrategy.getClass().getName() + ':' + System.identityHashCode(visibilityStrategy),
            System.identityHashCode(customizations),
            includeGeneratedIntrospections
        );
    }

    @Override
    public Object cacheKey() {
        return cacheKey;
    }

    @Override
    public <T> Optional<BeanIntrospection<T>> resolve(SerdeIntrospections.RuntimeIntrospectionRequest<T> request) {
        Class<T> type = request.argument().getType();
        if (!supports(type, includeGeneratedIntrospections)) {
            return Optional.empty();
        }
        JsonbRuntimeBeanIntrospection<?> scoped = scopedIntrospections.get(type);
        if (scoped != null) {
            @SuppressWarnings("unchecked")
            BeanIntrospection<T> typed = (BeanIntrospection<T>) scoped;
            return Optional.of(typed);
        }
        return Optional.of(introspection(type));
    }

    /**
     * Returns the shared runtime model for a type under this mapper's global
     * JSON-B configuration.
     *
     * @param type The bean type
     * @param <T> The bean type
     * @return The cached runtime introspection
     */
    @SuppressWarnings("unchecked")
    <T> JsonbRuntimeBeanIntrospection<T> introspection(Class<T> type) {
        return (JsonbRuntimeBeanIntrospection<T>) introspections.computeIfAbsent(type, this::createIntrospection);
    }

    /**
     * Returns a runtime model for a per-call visibility strategy. These models
     * are cached separately because visibility is part of the effective property
     * set and cannot be overlaid onto the global model.
     *
     * @param type The bean type
     * @param scopedVisibilityStrategy The visibility strategy selected for the current operation
     * @param <T> The bean type
     * @return The cached scoped runtime introspection
     */
    @SuppressWarnings("unchecked")
    <T> JsonbRuntimeBeanIntrospection<T> introspection(Class<T> type, PropertyVisibilityStrategy scopedVisibilityStrategy) {
        return (JsonbRuntimeBeanIntrospection<T>) scopedIntrospections.computeIfAbsent(
            type,
            beanType -> new JsonbRuntimeBeanIntrospection<>(beanType, namingStrategy, propertyOrderStrategy, scopedVisibilityStrategy, customizations)
        );
    }

    private JsonbRuntimeBeanIntrospection<?> createIntrospection(Class<?> type) {
        return new JsonbRuntimeBeanIntrospection<>(type, namingStrategy, propertyOrderStrategy, visibilityStrategy, customizations);
    }

    private static boolean supports(Class<?> type, boolean includeGeneratedIntrospections) {
        return type != Object.class
            && !JsonbReflectionUtil.isJsonScalar(type)
            && !JsonValue.class.isAssignableFrom(type)
            && !Optional.class.isAssignableFrom(type)
            && !type.isArray()
            && !Collection.class.isAssignableFrom(type)
            && !Map.class.isAssignableFrom(type)
            && (includeGeneratedIntrospections || BeanIntrospector.SHARED.findIntrospection(type).isEmpty());
    }
}
