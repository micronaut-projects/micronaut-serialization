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
package io.micronaut.serde.support;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.FormatConfiguration;
import io.micronaut.serde.FormattedDeserializer;
import io.micronaut.serde.FormattedSerializer;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.annotation.CacheableSpecificSerde;
import io.micronaut.serde.exceptions.SerdeException;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches the specific serdes of the beans annotated with {@link CacheableSpecificSerde}.
 *
 * @since 3.3.0
 */
@Internal
final class SpecificSerdeCache {

    /**
     * Bounds a cache when arguments are built with new annotation metadata on every call.
     */
    private static final int MAX_CACHED = 1024;

    private SpecificSerdeCache() {
    }

    static Serializer<?> serializer(DefaultSerdeRegistry registry, Serializer<?> serializer) {
        return new CachingSerializer<>(registry, serializer);
    }

    static Deserializer<?> deserializer(DefaultSerdeRegistry registry, Deserializer<?> deserializer) {
        return new CachingDeserializer<>(registry, deserializer);
    }

    private static <S> S putIfAbsent(Map<Key, S> cache, Key key, S serde) {
        if (cache.size() >= MAX_CACHED) {
            return serde;
        }
        S existing = cache.putIfAbsent(key, serde);
        return existing != null ? existing : serde;
    }

    /**
     * The key of a cached specific serde. {@link Argument#equals(Object)} ignores annotation metadata, which a
     * specific serde can read, so the metadata is compared by identity: the metadata of compiled arguments
     * is a constant, and an argument built with new metadata on every call misses the cache.
     *
     * @param type               The type
     * @param annotationMetadata The annotation metadata of the type, compared by identity
     * @param format             The format, if any
     */
    private record Key(Argument<?> type, AnnotationMetadata annotationMetadata, @Nullable FormatConfiguration format) {

        Key(Argument<?> type, @Nullable FormatConfiguration format) {
            this(type, type.getAnnotationMetadata(), format);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key that
                && annotationMetadata == that.annotationMetadata
                && type.equals(that.type)
                && Objects.equals(format, that.format);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * type.hashCode() + System.identityHashCode(annotationMetadata)) + Objects.hashCode(format);
        }
    }

    private static final class CachingSerializer<T> implements FormattedSerializer<T> {

        private final DefaultSerdeRegistry registry;
        private final Serializer<T> serializer;
        private final Map<Key, Serializer<T>> cache = new ConcurrentHashMap<>();

        @SuppressWarnings("unchecked")
        private CachingSerializer(DefaultSerdeRegistry registry, Serializer<?> serializer) {
            this.registry = registry;
            this.serializer = (Serializer<T>) serializer;
        }

        @Override
        public Serializer<T> createSpecific(EncoderContext context, Argument<? extends T> type) throws SerdeException {
            return createSpecific(context, type, null);
        }

        @Override
        public Serializer<T> createSpecific(EncoderContext context,
                                            Argument<? extends T> type,
                                            @Nullable FormatConfiguration format) throws SerdeException {
            // A context with a view, or another implementation, can change what the serializer creates
            if (context.getClass() != DefaultEncoderContext.class || ((DefaultEncoderContext) context).registry != registry) {
                return create(context, type, format);
            }
            Key key = new Key(type, format);
            Serializer<T> specific = cache.get(key);
            if (specific == null) {
                // Created with a context of the registry: a cached serializer must not retain a document's context
                specific = putIfAbsent(cache, key, create(new DefaultEncoderContext(registry), type, format));
            }
            return specific;
        }

        @SuppressWarnings("unchecked")
        private Serializer<T> create(EncoderContext context,
                                     Argument<? extends T> type,
                                     @Nullable FormatConfiguration format) throws SerdeException {
            if (format != null && serializer instanceof FormattedSerializer<?> formattedSerializer) {
                return ((FormattedSerializer<T>) formattedSerializer).createSpecific(context, type, format);
            }
            return serializer.createSpecific(context, type);
        }

        @Override
        public void serialize(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException {
            createSpecific(context, type).serialize(encoder, context, type, value);
        }
    }

    private static final class CachingDeserializer<T> implements FormattedDeserializer<T> {

        private final DefaultSerdeRegistry registry;
        private final Deserializer<T> deserializer;
        private final Map<Key, Deserializer<T>> cache = new ConcurrentHashMap<>();

        @SuppressWarnings("unchecked")
        private CachingDeserializer(DefaultSerdeRegistry registry, Deserializer<?> deserializer) {
            this.registry = registry;
            this.deserializer = (Deserializer<T>) deserializer;
        }

        @Override
        public Deserializer<T> createSpecific(DecoderContext context, Argument<? super T> type) throws SerdeException {
            return createSpecific(context, type, null);
        }

        @Override
        public Deserializer<T> createSpecific(DecoderContext context,
                                              Argument<? super T> type,
                                              @Nullable FormatConfiguration format) throws SerdeException {
            // A context with a view, or another implementation, can change what the deserializer creates
            if (context.getClass() != DefaultDecoderContext.class || ((DefaultDecoderContext) context).registry != registry) {
                return create(context, type, format);
            }
            Key key = new Key(type, format);
            Deserializer<T> specific = cache.get(key);
            if (specific == null) {
                // Created with a context of the registry: a cached deserializer must not retain a document's context
                specific = putIfAbsent(cache, key, create(new DefaultDecoderContext(registry), type, format));
            }
            return specific;
        }

        @SuppressWarnings("unchecked")
        private Deserializer<T> create(DecoderContext context,
                                       Argument<? super T> type,
                                       @Nullable FormatConfiguration format) throws SerdeException {
            if (format != null && deserializer instanceof FormattedDeserializer<?> formattedDeserializer) {
                return ((FormattedDeserializer<T>) formattedDeserializer).createSpecific(context, type, format);
            }
            return deserializer.createSpecific(context, type);
        }

        @Override
        public T deserialize(Decoder decoder, DecoderContext context, Argument<? super T> type) throws IOException {
            return createSpecific(context, type).deserialize(decoder, context, type);
        }

        @Override
        public @Nullable T deserializeNullable(Decoder decoder, DecoderContext context, Argument<? super T> type) throws IOException {
            return createSpecific(context, type).deserializeNullable(decoder, context, type);
        }
    }
}
