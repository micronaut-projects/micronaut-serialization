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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.serde.config.annotation.SerdeConfig;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.annotation.JsonbCreator;
import jakarta.json.bind.annotation.JsonbProperty;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Objects;

/**
 * Constructor/factory adapter for runtime JSON-B introspections.
 * <p>
 * It supports the instantiation forms exercised by JSON-B reflection fallback:
 * default constructors, record canonical constructors, and a single
 * {@link JsonbCreator} constructor or static factory. It deliberately does not
 * implement broader Micronaut construction features because the runtime
 * introspection is not a general replacement for generated metadata.
 *
 * @param <T> The bean type
 */
final class JsonbRuntimeBeanConstructor<T> implements BeanConstructor<T> {
    private final Class<T> type;
    private final @Nullable Constructor<T> constructor;
    private final @Nullable Method factory;
    private final Argument<?>[] arguments;
    private final AnnotationMetadata annotationMetadata;

    /**
     * Creates the Micronaut constructor view for the chosen JSON-B construction
     * member. Exactly one of {@code constructor} or {@code factory} should be
     * present; neither is present for default-constructor-only fallback models.
     *
     * @param type The bean type
     * @param constructor The selected constructor
     * @param factory The selected static factory method
     */
    private JsonbRuntimeBeanConstructor(Class<T> type, @Nullable Constructor<T> constructor, @Nullable Method factory) {
        this.type = type;
        this.constructor = constructor;
        this.factory = factory;
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        Executable executable = constructor == null ? factory : constructor;
        if (executable == null) {
            this.arguments = Argument.ZERO_ARGUMENTS;
        } else {
            this.arguments = arguments(executable);
            if (executable.isAnnotationPresent(JsonbCreator.class)) {
                metadata.addAnnotation(Creator.class.getName(), Map.of("mode", SerdeConfig.SerCreatorMode.PROPERTIES));
            }
        }
        this.annotationMetadata = metadata;
    }

    /**
     * Discovers the JSON-B construction member for a runtime introspection.
     * Selection order mirrors JSON-B fallback behavior: explicit
     * {@link JsonbCreator}, then record canonical constructor, then default
     * constructor.
     *
     * @param type The bean type
     * @param <T> The bean type
     * @return The runtime constructor adapter
     */
    @SuppressWarnings({"unchecked", "java:S3776"})
    static <T> JsonbRuntimeBeanConstructor<T> of(Class<T> type) {
        Constructor<T> creatorConstructor = null;
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (constructor.isAnnotationPresent(JsonbCreator.class)) {
                creatorConstructor = (Constructor<T>) constructor;
                break;
            }
        }
        if (creatorConstructor != null) {
            return new JsonbRuntimeBeanConstructor<>(type, creatorConstructor, null);
        }
        for (Method method : type.getDeclaredMethods()) {
            if (method.isAnnotationPresent(JsonbCreator.class)) {
                return new JsonbRuntimeBeanConstructor<>(type, null, method);
            }
        }
        if (type.isRecord()) {
            RecordComponent[] recordComponents = type.getRecordComponents();
            Class<?>[] componentTypes = new Class<?>[recordComponents.length];
            for (int i = 0; i < recordComponents.length; i++) {
                componentTypes[i] = recordComponents[i].getType();
            }
            return ReflectionUtils.findConstructor(type, componentTypes)
                .map(constructor -> new JsonbRuntimeBeanConstructor<>(type, constructor, null))
                .orElseGet(() -> new JsonbRuntimeBeanConstructor<>(type, null, null));
        }
        return ReflectionUtils.findConstructor(type)
            .map(constructor -> new JsonbRuntimeBeanConstructor<>(type, constructor, null))
            .orElseGet(() -> new JsonbRuntimeBeanConstructor<>(type, null, null));
    }

    @Override
    public Class<T> getDeclaringBeanType() {
        return type;
    }

    @Override
    public Argument<?>[] getArguments() {
        return arguments;
    }

    @Override
    public T instantiate(Object... parameterValues) {
        if (constructor != null) {
            if (!constructor.trySetAccessible()) {
                throw new JsonbException("Cannot access JSON-B fallback constructor for " + type.getName());
            }
            return InstantiationUtils.tryInstantiate(constructor, parameterValues)
                .orElseThrow(() -> new JsonbException("Cannot instantiate JSON-B fallback type " + type.getName()));
        }
        if (factory != null) {
            try {
                return Objects.requireNonNull(ReflectionUtils.invokeInaccessibleMethod(type, factory, parameterValues));
            } catch (RuntimeException e) {
                throw new JsonbException("Cannot instantiate JSON-B fallback type " + type.getName(), e);
            }
        }
        return JsonbReflectionUtil.instantiate(type);
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    /**
     * Builds constructor argument metadata from Java reflection parameters.
     * {@link JsonbProperty} names are translated to Serde property metadata so
     * existing generated deserializer machinery can bind the runtime model.
     *
     * @param executable The constructor or factory method
     * @return The constructor arguments exposed to Serde
     */
    private static Argument<?>[] arguments(Executable executable) {
        Parameter[] parameters = executable.getParameters();
        Type[] genericTypes = executable.getGenericParameterTypes();
        Argument<?>[] arguments = new Argument<?>[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
            JsonbProperty property = parameter.getAnnotation(JsonbProperty.class);
            String name = property != null && !property.value().isEmpty() ? property.value() : parameter.getName();
            if (property != null && !property.value().isEmpty()) {
                metadata.addAnnotation(SerdeConfig.class.getName(), Map.of(SerdeConfig.PROPERTY, property.value()));
            }
            arguments[i] = Argument.of(genericTypes[i]).withName(name).withAnnotationMetadata(metadata);
        }
        return arguments;
    }
}
