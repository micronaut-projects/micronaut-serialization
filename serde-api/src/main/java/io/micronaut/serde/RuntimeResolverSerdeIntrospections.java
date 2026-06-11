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
package io.micronaut.serde;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.config.annotation.SerdeConfig;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolver-backed introspections wrapper.
 */
final class RuntimeResolverSerdeIntrospections implements SerdeIntrospections {
    private final SerdeIntrospections delegate;
    private final RuntimeIntrospectionResolver resolver;
    private final Map<RuntimeIntrospectionCacheKey, Optional<BeanIntrospection<?>>> runtimeIntrospections = new ConcurrentHashMap<>();

    RuntimeResolverSerdeIntrospections(SerdeIntrospections delegate, RuntimeIntrospectionResolver resolver) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    @Override
    public BeanIntrospector getBeanIntrospector() {
        return delegate.getBeanIntrospector();
    }

    @Override
    public <T> BeanIntrospection<T> getSerializableIntrospection(Argument<T> type) {
        return resolve(type, RuntimeIntrospectionKind.SERIALIZATION)
            .orElseGet(() -> delegate.getSerializableIntrospection(type));
    }

    @Override
    public <T> BeanIntrospection<T> getDeserializableIntrospection(Argument<T> type) {
        return resolve(type, RuntimeIntrospectionKind.DESERIALIZATION)
            .orElseGet(() -> delegate.getDeserializableIntrospection(type));
    }

    @Override
    public <T> Collection<BeanIntrospection<? extends T>> findSubtypeDeserializables(Class<T> type) {
        List<BeanIntrospection<? extends T>> subtypeIntrospections = new ArrayList<>(delegate.findSubtypeDeserializables(type));
        resolve(Argument.of(type), RuntimeIntrospectionKind.DESERIALIZATION)
            .ifPresent(introspection -> {
                for (AnnotationValue<SerdeConfig.SerSubtyped.SerSubtype> subtype : introspection.getAnnotationMetadata().getAnnotationValuesByType(SerdeConfig.SerSubtyped.SerSubtype.class)) {
                    Optional<Class<?>> subtypeClass = subtype.classValue(AnnotationMetadata.VALUE_MEMBER);
                    if (subtypeClass.isPresent() && type.isAssignableFrom(subtypeClass.get())) {
                        @SuppressWarnings({"unchecked", "rawtypes"})
                        Argument<? extends T> subtypeArgument = (Argument) Argument.of(subtypeClass.get());
                        resolve(subtypeArgument, RuntimeIntrospectionKind.DESERIALIZATION)
                            .ifPresent(subtypeIntrospection -> {
                                if (subtypeIntrospections.stream().noneMatch(existing -> existing.getBeanType() == subtypeIntrospection.getBeanType())) {
                                    subtypeIntrospections.add(subtypeIntrospection);
                                }
                            });
                    }
                }
            });
        return subtypeIntrospections;
    }

    private <T> Optional<BeanIntrospection<T>> resolve(Argument<T> type, RuntimeIntrospectionKind kind) {
        RuntimeIntrospectionCacheKey key = new RuntimeIntrospectionCacheKey(kind, type, resolver.cacheKey());
        Optional<BeanIntrospection<?>> introspection = runtimeIntrospections.computeIfAbsent(key, ignored ->
            resolver.resolve(new RuntimeIntrospectionRequest<>(type, kind)).map(i -> i)
        );
        @SuppressWarnings("unchecked")
        Optional<BeanIntrospection<T>> typed = (Optional<BeanIntrospection<T>>) (Optional<?>) introspection;
        return typed;
    }

    private record RuntimeIntrospectionCacheKey(
        RuntimeIntrospectionKind kind,
        Argument<?> argument,
        @Nullable Object resolverCacheKey
    ) {
    }
}
