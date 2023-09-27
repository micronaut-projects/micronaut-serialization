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
package io.micronaut.serde.support.deserializers;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Primary;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.SerdeIntrospections;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.util.TypeKey;
import io.micronaut.serde.util.CustomizableDeserializer;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Implementation for deserialization of objects that uses introspection metadata.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Singleton
@Primary
@BootstrapContextCompatible
public class ObjectDeserializer implements CustomizableDeserializer<Object>, DeserBeanRegistry {
    private final SerdeIntrospections introspections;
    private final boolean ignoreUnknown;
    private final boolean strictNullable;
    private final Map<TypeKey, Supplier<DeserBean<?>>> deserBeanMap = new ConcurrentHashMap<>(50);
    @Nullable
    private final SerdeDeserializationPreInstantiateCallback preInstantiateCallback;

    public ObjectDeserializer(SerdeIntrospections introspections,
                              DeserializationConfiguration deserializationConfiguration,
                              @Nullable SerdeDeserializationPreInstantiateCallback preInstantiateCallback) {
        this.introspections = introspections;
        this.ignoreUnknown = deserializationConfiguration.isIgnoreUnknown();
        this.strictNullable = deserializationConfiguration.isStrictNullable();
        this.preInstantiateCallback = preInstantiateCallback;
    }

    @Override
    public Deserializer<Object> createSpecific(DecoderContext context, Argument<? super Object> type) throws SerdeException {
        if (type.equalsType(Argument.OBJECT_ARGUMENT)) {
            // fallback to dynamic resolution
            return (Decoder decoder, DecoderContext context1, Argument<? super Object> type1) -> decoder.decodeArbitrary();
        }
        DeserBean<? super Object> deserBean = getDeserializableBean(type, context);
        if (deserBean.simpleBean) {
            return new SimpleObjectDeserializer(ignoreUnknown, strictNullable, deserBean, preInstantiateCallback);
        }
        if (deserBean.recordLikeBean) {
            return new SimpleRecordLikeObjectDeserializer(ignoreUnknown, strictNullable, deserBean, preInstantiateCallback);
        }
        return new SpecificObjectDeserializer(ignoreUnknown, strictNullable, deserBean, preInstantiateCallback);
    }

    @Override
    public <T> DeserBean<T> getDeserializableBean(Argument<T> type, DecoderContext decoderContext) throws SerdeException {
        TypeKey key = new TypeKey(type);
        // Use suppliers to prevent recursive update because the lambda will can call the same method again
        Supplier<DeserBean<?>> deserBeanSupplier = deserBeanMap.computeIfAbsent(key, ignore -> SupplierUtil.memoizedNonEmpty(() -> createDeserBean(type, decoderContext)));
        DeserBean<?> deserBean = deserBeanSupplier.get();
        deserBean.initialize(decoderContext);
        return (DeserBean<T>) deserBean;
    }

    private <T> DeserBean<T> createDeserBean(Argument<T> type, DecoderContext decoderContext) {
        try {
            final BeanIntrospection<T> deserializableIntrospection = introspections.getDeserializableIntrospection(type);
            AnnotationMetadata annotationMetadata = new AnnotationMetadataHierarchy(
                    type.getAnnotationMetadata(),
                    deserializableIntrospection.getAnnotationMetadata()
            );
            if (annotationMetadata.hasAnnotation(SerdeConfig.SerSubtyped.class)) {
                if (type.hasTypeVariables()) {
                    final Map<String, Argument<?>> bounds = type.getTypeVariables();
                    return new SubtypedDeserBean<>(annotationMetadata, deserializableIntrospection, decoderContext, this) {
                        @Override
                        protected Map<String, Argument<?>> getBounds() {
                            return bounds;
                        }
                    };
                } else {
                    return new SubtypedDeserBean<>(annotationMetadata, deserializableIntrospection, decoderContext, this);
                }
            } else {
                if (type.hasTypeVariables()) {
                    final Map<String, Argument<?>> bounds = type.getTypeVariables();
                    return new DeserBean<>(deserializableIntrospection, decoderContext, this) {
                        @Override
                        protected Map<String, Argument<?>> getBounds() {
                            return bounds;
                        }
                    };
                } else {
                    return new DeserBean<>(deserializableIntrospection, decoderContext, this);
                }
            }
        } catch (SerdeException e) {
            throw new IntrospectionException("Error creating deserializer for type [" + type + "]: " + e.getMessage(), e);
        }
    }
}
