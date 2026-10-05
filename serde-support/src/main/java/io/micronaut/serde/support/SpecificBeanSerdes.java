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

import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.FormatConfiguration;
import io.micronaut.serde.FormattedDeserializer;
import io.micronaut.serde.FormattedSerializer;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.annotation.CacheableSpecificSerde;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.util.CustomizableDeserializer;
import io.micronaut.serde.util.CustomizableSerializer;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serdes of the beans that take the encoder or decoder context and the type as constructor arguments.
 * A bean annotated with {@link CacheableSpecificSerde} is created once per type and format, other beans
 * are created for every specific serde, with the context of the call.
 *
 * @since 3.2.5
 */
@Internal
final class SpecificBeanSerdes {

    private SpecificBeanSerdes() {
    }

    @SuppressWarnings("rawtypes")
    static Serializer<?> serializer(DefaultSerdeRegistry registry, BeanDefinition<Serializer> beanDefinition) {
        return new SpecificBeanSerializer(registry, beanDefinition.getBeanType(), isCacheable(beanDefinition));
    }

    @SuppressWarnings("rawtypes")
    static Deserializer<?> deserializer(DefaultSerdeRegistry registry, BeanDefinition<Deserializer> beanDefinition) {
        return new SpecificBeanDeserializer(registry, beanDefinition.getBeanType(), isCacheable(beanDefinition));
    }

    private static boolean isCacheable(BeanDefinition<?> beanDefinition) {
        return beanDefinition.hasDeclaredAnnotation(CacheableSpecificSerde.class);
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
    private record SpecificKey(Argument<?> type, AnnotationMetadata annotationMetadata, @Nullable FormatConfiguration format) {

        SpecificKey(Argument<?> type, @Nullable FormatConfiguration format) {
            this(type, type.getAnnotationMetadata(), format);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof SpecificKey that
                && annotationMetadata == that.annotationMetadata
                && type.equals(that.type)
                && Objects.equals(format, that.format);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * type.hashCode() + System.identityHashCode(annotationMetadata)) + Objects.hashCode(format);
        }
    }

    /**
     * Creates the specific serdes of a bean, cached when the bean allows it.
     *
     * @param <C> The context type
     * @param <S> The serde type
     */
    private abstract static class SpecificBeanSerde<C, S> {

        /**
         * Bounds the cache when arguments are built with new annotation metadata on every call.
         */
        private static final int MAX_CACHED = 1024;

        final DefaultSerdeRegistry registry;
        @Nullable
        private final Map<SpecificKey, S> cache;

        SpecificBeanSerde(DefaultSerdeRegistry registry, boolean cacheable) {
            this.registry = registry;
            this.cache = cacheable ? new ConcurrentHashMap<>() : null;
        }

        final S createCached(C context, Argument<?> type, @Nullable FormatConfiguration format) throws SerdeException {
            Map<SpecificKey, S> cache = this.cache;
            // A context with a view, or another implementation, can change what the bean resolves:
            // only a plain context of this registry depends on nothing but the registry
            if (cache == null || !isRegistryContext(context)) {
                return create(context, type, format);
            }
            SpecificKey key = new SpecificKey(type, format);
            S serde = cache.get(key);
            if (serde == null) {
                // Created with a context owned by the registry: a cached serde must not retain the document-scoped
                // context of the call that created it, and a plain context resolves the same serde
                serde = create(newRegistryContext(), type, format);
                if (cache.size() < MAX_CACHED) {
                    S existing = cache.putIfAbsent(key, serde);
                    if (existing != null) {
                        serde = existing;
                    }
                }
            }
            return serde;
        }

        abstract boolean isRegistryContext(C context);

        abstract C newRegistryContext();

        abstract S create(C context, Argument<?> type, @Nullable FormatConfiguration format) throws SerdeException;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final class SpecificBeanSerializer extends SpecificBeanSerde<Serializer.EncoderContext, Serializer<Object>>
        implements CustomizableSerializer<Object>, FormattedSerializer<Object> {

        private final Class<? extends Serializer> beanType;

        private SpecificBeanSerializer(DefaultSerdeRegistry registry, Class<? extends Serializer> beanType, boolean cacheable) {
            super(registry, cacheable);
            this.beanType = beanType;
        }

        @Override
        public Serializer<Object> createSpecific(Serializer.EncoderContext context,
                                                 Argument<? extends Object> type) throws SerdeException {
            return createSpecific(context, type, null);
        }

        @Override
        public Serializer<Object> createSpecific(Serializer.EncoderContext context,
                                                 Argument<? extends Object> type,
                                                 @Nullable FormatConfiguration format) throws SerdeException {
            return createCached(context, type, format);
        }

        @Override
        boolean isRegistryContext(Serializer.EncoderContext context) {
            return context.getClass() == DefaultEncoderContext.class && ((DefaultEncoderContext) context).registry == registry;
        }

        @Override
        Serializer.EncoderContext newRegistryContext() {
            return new DefaultEncoderContext(registry);
        }

        @Override
        Serializer<Object> create(Serializer.EncoderContext context, Argument<?> type, @Nullable FormatConfiguration format) throws SerdeException {
            BeanContext beanContext = registry.getBeanContext();
            Serializer serializer = beanContext.createBean(beanType, context, type);
            if (format != null && serializer instanceof FormattedSerializer formattedSerializer) {
                return formattedSerializer.createSpecific(context, type, format);
            }
            return serializer.createSpecific(context, type);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final class SpecificBeanDeserializer extends SpecificBeanSerde<Deserializer.DecoderContext, Deserializer<Object>>
        implements CustomizableDeserializer<Object>, FormattedDeserializer<Object> {

        private final Class<? extends Deserializer> beanType;

        private SpecificBeanDeserializer(DefaultSerdeRegistry registry, Class<? extends Deserializer> beanType, boolean cacheable) {
            super(registry, cacheable);
            this.beanType = beanType;
        }

        @Override
        public Deserializer<Object> createSpecific(Deserializer.DecoderContext context,
                                                   Argument<? super Object> type) throws SerdeException {
            return createSpecific(context, type, null);
        }

        @Override
        public Deserializer<Object> createSpecific(Deserializer.DecoderContext context,
                                                   Argument<? super Object> type,
                                                   @Nullable FormatConfiguration format) throws SerdeException {
            return createCached(context, type, format);
        }

        @Override
        boolean isRegistryContext(Deserializer.DecoderContext context) {
            return context.getClass() == DefaultDecoderContext.class && ((DefaultDecoderContext) context).registry == registry;
        }

        @Override
        Deserializer.DecoderContext newRegistryContext() {
            return new DefaultDecoderContext(registry);
        }

        @Override
        Deserializer<Object> create(Deserializer.DecoderContext context, Argument<?> type, @Nullable FormatConfiguration format) throws SerdeException {
            BeanContext beanContext = registry.getBeanContext();
            Deserializer deserializer = beanContext.createBean(beanType, context, type);
            if (format != null && deserializer instanceof FormattedDeserializer formattedDeserializer) {
                return formattedDeserializer.createSpecific(context, type, format);
            }
            return deserializer.createSpecific(context, type);
        }
    }
}
