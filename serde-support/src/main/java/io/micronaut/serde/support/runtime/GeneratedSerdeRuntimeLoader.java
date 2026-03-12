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
package io.micronaut.serde.support.runtime;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.annotation.SerdeConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Internal
public final class GeneratedSerdeRuntimeLoader {

    private final Map<String, LookupResult<Serializer<?>>> serializerCache = new ConcurrentHashMap<>(20);
    private final Map<String, LookupResult<Deserializer<?>>> deserializerCache = new ConcurrentHashMap<>(20);

    public LookupResult<Serializer<?>> loadSerializer(BeanIntrospection<?> introspection, Argument<?> type) {
        AnnotationMetadata annotationMetadata = introspection.getAnnotationMetadata();
        if (!annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.SOURCEGEN_SERIALIZER_ELIGIBLE).orElse(false)) {
            return LookupResult.unavailable();
        }
        String className = annotationMetadata.stringValue(SerdeConfig.class, SerdeConfig.SOURCEGEN_SERIALIZER_CLASS).orElse(null);
        if (className == null || className.isBlank()) {
            return LookupResult.unavailable();
        }
        return serializerCache.computeIfAbsent(className, n -> instantiateSerializer(type, n));
    }

    public LookupResult<Deserializer<?>> loadDeserializer(BeanIntrospection<?> introspection, Argument<?> type) {
        AnnotationMetadata annotationMetadata = introspection.getAnnotationMetadata();
        if (!annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.SOURCEGEN_DESERIALIZER_ELIGIBLE).orElse(false)) {
            return LookupResult.unavailable();
        }
        String className = annotationMetadata.stringValue(SerdeConfig.class, SerdeConfig.SOURCEGEN_DESERIALIZER_CLASS).orElse(null);
        if (className == null || className.isBlank()) {
            return LookupResult.unavailable();
        }
        return deserializerCache.computeIfAbsent(className, n -> instantiateDeserializer(type, n));
    }

    private static LookupResult<Serializer<?>> instantiateSerializer(Argument<?> type, String className) {
        return instantiate(type, className, Serializer.class, "serializer").cast();
    }

    private static LookupResult<Deserializer<?>> instantiateDeserializer(Argument<?> type, String className) {
        return instantiate(type, className, Deserializer.class, "deserializer").cast();
    }

    private static <T> LookupResult<T> instantiate(Argument<?> type,
                                                    String className,
                                                    Class<T> expectedType,
                                                    String kind) {
        ClassLoader classLoader = type.getType().getClassLoader();
        Class<?> generatedClass = ClassUtils.forName(className, classLoader)
            .orElseGet(() -> ClassUtils.forName(className, GeneratedSerdeRuntimeLoader.class.getClassLoader()).orElse(null));
        if (generatedClass == null) {
            return LookupResult.failed("Cannot load generated " + kind + " class [" + className + "] for type [" + type + "]");
        }
        if (!expectedType.isAssignableFrom(generatedClass)) {
            return LookupResult.failed("Generated " + kind + " class [" + className + "] is not a " + expectedType.getSimpleName());
        }
        try {
            Object instance = generatedClass.getDeclaredConstructor().newInstance();
            return LookupResult.available(expectedType.cast(instance));
        } catch (ReflectiveOperationException | LinkageError e) {
            return LookupResult.failed("Failed to instantiate generated " + kind + " class [" + className + "]: " + e.getMessage());
        }
    }

    @Internal
    public static final class LookupResult<T> {

        private final Status status;
        private final T value;
        private final String message;

        private LookupResult(Status status, T value, String message) {
            this.status = status;
            this.value = value;
            this.message = message;
        }

        public static <T> LookupResult<T> available(T value) {
            return new LookupResult<>(Status.AVAILABLE, value, null);
        }

        public static <T> LookupResult<T> unavailable() {
            return new LookupResult<>(Status.UNAVAILABLE, null, null);
        }

        public static <T> LookupResult<T> failed(String message) {
            return new LookupResult<>(Status.FAILED, null, message);
        }

        public Status status() {
            return status;
        }

        public T value() {
            return value;
        }

        public String message() {
            return message;
        }

        @SuppressWarnings("unchecked")
        private <S> LookupResult<S> cast() {
            return (LookupResult<S>) this;
        }
    }

    @Internal
    public enum Status {
        AVAILABLE,
        UNAVAILABLE,
        FAILED
    }
}
