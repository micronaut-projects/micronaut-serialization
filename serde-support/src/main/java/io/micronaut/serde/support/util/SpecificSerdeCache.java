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
package io.micronaut.serde.support.util;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.GenericPlaceholder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.DeserializerLocator;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.SerializerLocator;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.util.DocumentContextTracker;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The specific serdes a mapper creates for the documents it reads and writes, kept for the next
 * documents when creating them did not bind them to the document.
 *
 * @since 3.3
 */
@Internal
public final class SpecificSerdeCache {

    private static final int MAX_CACHED_SERDES = 256;

    private final Map<Argument<?>, Cached<Serializer<?>>> serializers = new ConcurrentHashMap<>();
    private final Map<Argument<?>, Cached<Deserializer<?>>> deserializers = new ConcurrentHashMap<>();

    /**
     * Finds the specific serializer of a type.
     *
     * @param context The context of the document
     * @param type    The type
     * @param <T>     The type
     * @return The specific serializer
     * @throws SerdeException If no serializer is found
     */
    public <T> Serializer<T> findSerializer(Serializer.EncoderContext context, Argument<T> type) throws SerdeException {
        return findSerializer(context, context, type);
    }

    /**
     * Finds the specific serializer of a type, looked up in the given locator, such as the registry of
     * the mapper, when it is not cached.
     *
     * @param locator The locator of the serializer
     * @param context The context of the document
     * @param type    The type
     * @param <T>     The type
     * @return The specific serializer
     * @throws SerdeException If no serializer is found
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> Serializer<T> findSerializer(SerializerLocator locator, Serializer.EncoderContext context, Argument<T> type) throws SerdeException {
        boolean cacheable = !type.isTypeVariable() && isCacheable(type);
        if (cacheable) {
            Cached<Serializer<?>> cached = serializers.get(type);
            if (cached != null) {
                if (cached.matches(type)) {
                    return (Serializer<T>) cached.serde();
                }
                // an equal argument whose type parameters are placeholders in other places is not cached
                cacheable = false;
            }
        }
        Serializer<? super T> found = locator.findSerializer(type);
        // A locator other than the context, such as a registry that overrides some serdes, is cached only
        // when the context finds the same serde, so that the context could tell whether it is bound
        cacheable = cacheable && (locator == context || context.findSerializer(type) == found);
        Serializer<T> serializer = (Serializer<T>) found.createSpecific(context, (Argument) type);
        if (cacheable && !DocumentContextTracker.isDocumentBound(context) && serializers.size() < MAX_CACHED_SERDES) {
            serializers.putIfAbsent(type, new Cached<>(type, serializer));
        }
        return serializer;
    }

    /**
     * Finds the specific deserializer of a type.
     *
     * @param context The context of the document
     * @param type    The type
     * @param <T>     The type
     * @return The specific deserializer
     * @throws SerdeException If no deserializer is found
     */
    public <T> Deserializer<? extends T> findDeserializer(Deserializer.DecoderContext context, Argument<T> type) throws SerdeException {
        return findDeserializer(context, context, type);
    }

    /**
     * Finds the specific deserializer of a type, looked up in the given locator, such as the registry of
     * the mapper, when it is not cached.
     *
     * @param locator The locator of the deserializer
     * @param context The context of the document
     * @param type    The type
     * @param <T>     The type
     * @return The specific deserializer
     * @throws SerdeException If no deserializer is found
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> Deserializer<? extends T> findDeserializer(DeserializerLocator locator, Deserializer.DecoderContext context, Argument<T> type) throws SerdeException {
        boolean cacheable = !type.isTypeVariable() && isCacheable(type);
        if (cacheable) {
            Cached<Deserializer<?>> cached = deserializers.get(type);
            if (cached != null) {
                if (cached.matches(type)) {
                    return (Deserializer<? extends T>) cached.serde();
                }
                cacheable = false;
            }
        }
        Deserializer<? extends T> found = locator.findDeserializer(type);
        // A locator other than the context, such as a registry that overrides some serdes, is cached only
        // when the context finds the same serde, so that the context could tell whether it is bound
        cacheable = cacheable && (locator == context || context.findDeserializer(type) == found);
        Deserializer<? extends T> deserializer = (Deserializer<? extends T>) found.createSpecific(context, (Argument) type);
        if (cacheable && !DocumentContextTracker.isDocumentBound(context) && deserializers.size() < MAX_CACHED_SERDES) {
            deserializers.putIfAbsent(type, new Cached<>(type, deserializer));
        }
        return deserializer;
    }

    /**
     * The number of cached serializers and deserializers.
     *
     * @return The size
     */
    public int size() {
        return serializers.size() + deserializers.size();
    }

    /**
     * The equality of arguments ignores their annotations, which can change the serde, so only
     * types without annotations are cached.
     *
     * @param type The type
     * @return Whether the serde of the type can be cached by the type
     */
    private static boolean isCacheable(Argument<?> type) {
        // A type variable equals the concrete argument of its type, but is serialized by runtime type, so the root
        // argument of a call is not cached when it is one (see the callers). The type parameters of an argument such as
        // Argument.of(ArrayList.class, Integer.class) are type variables bound to concrete types, and stay cacheable.
        if (!type.getAnnotationMetadata().isEmpty()) {
            return false;
        }
        for (Argument<?> typeParameter : type.getTypeParameters()) {
            if (!isCacheable(typeParameter)) {
                return false;
            }
        }
        return true;
    }

    /**
     * A cached serde with the argument it was created for.
     *
     * @param type  The argument
     * @param serde The serde
     * @param <S>   The serde type
     */
    private record Cached<S>(Argument<?> type, S serde) {

        /**
         * {@link Argument#equals(Object)} ignores whether a type parameter is a generic placeholder, but the element
         * of a placeholder, such as the type variable of {@code Argument.of(List.class, Base.class)} or a wildcard,
         * is written by its runtime type, and the element of {@code Argument.listOf(Base.class)} by its declared type.
         *
         * @param other An argument equal to the cached one
         * @return Whether the cached serde serves it
         */
        boolean matches(Argument<?> other) {
            return other == type || samePlaceholders(type, other);
        }

        private static boolean samePlaceholders(Argument<?> a, Argument<?> b) {
            Argument<?>[] parametersA = a.getTypeParameters();
            Argument<?>[] parametersB = b.getTypeParameters();
            if (parametersA.length != parametersB.length) {
                return false;
            }
            for (int i = 0; i < parametersA.length; i++) {
                if (parametersA[i] instanceof GenericPlaceholder<?> != parametersB[i] instanceof GenericPlaceholder<?>
                    || !samePlaceholders(parametersA[i], parametersB[i])) {
                    return false;
                }
            }
            return true;
        }
    }
}
