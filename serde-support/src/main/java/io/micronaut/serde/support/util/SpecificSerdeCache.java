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
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Serializer;
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

    private final Map<Argument<?>, Serializer<?>> serializers = new ConcurrentHashMap<>();
    private final Map<Argument<?>, Deserializer<?>> deserializers = new ConcurrentHashMap<>();

    /**
     * Finds the specific serializer of a type.
     *
     * @param context The context of the document
     * @param type    The type
     * @param <T>     The type
     * @return The specific serializer
     * @throws SerdeException If no serializer is found
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> Serializer<T> findSerializer(Serializer.EncoderContext context, Argument<T> type) throws SerdeException {
        boolean cacheable = isCacheable(type);
        if (cacheable) {
            Serializer<?> serializer = serializers.get(type);
            if (serializer != null) {
                return (Serializer<T>) serializer;
            }
        }
        Serializer<T> serializer = (Serializer<T>) context.findSerializer(type).createSpecific(context, (Argument) type);
        if (cacheable && !DocumentContextTracker.isDocumentBound(context) && serializers.size() < MAX_CACHED_SERDES) {
            serializers.putIfAbsent(type, serializer);
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
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> Deserializer<? extends T> findDeserializer(Deserializer.DecoderContext context, Argument<T> type) throws SerdeException {
        boolean cacheable = isCacheable(type);
        if (cacheable) {
            Deserializer<?> deserializer = deserializers.get(type);
            if (deserializer != null) {
                return (Deserializer<? extends T>) deserializer;
            }
        }
        Deserializer<? extends T> deserializer = (Deserializer<? extends T>) context.findDeserializer(type).createSpecific(context, (Argument) type);
        if (cacheable && !DocumentContextTracker.isDocumentBound(context) && deserializers.size() < MAX_CACHED_SERDES) {
            deserializers.putIfAbsent(type, deserializer);
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
}
