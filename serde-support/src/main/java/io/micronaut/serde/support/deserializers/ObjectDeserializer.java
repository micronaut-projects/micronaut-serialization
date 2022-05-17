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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.micronaut.context.annotation.Primary;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.type.Argument;
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

/**
 * Implementation for deserialization of objects that uses introspection metadata.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Singleton
@Primary
public class ObjectDeserializer implements CustomizableDeserializer<Object>, DeserBeanRegistry {
    private final SerdeIntrospections introspections;
    private final boolean ignoreUnknown;
    private final Map<TypeKey, DeserBean<? super Object>> deserBeanMap = new ConcurrentHashMap<>(50);

    public ObjectDeserializer(SerdeIntrospections introspections, DeserializationConfiguration deserializationConfiguration) {
        this.introspections = introspections;
        this.ignoreUnknown = deserializationConfiguration.isIgnoreUnknown();
    }

    @Override
    public Deserializer<Object> createSpecific(DecoderContext context, Argument<? super Object> type) throws SerdeException {
        if (type.equalsType(Argument.OBJECT_ARGUMENT)) {
            // fallback to dynamic resolution
            return (Decoder decoder, DecoderContext context1, Argument<? super Object> type1) -> decoder.decodeArbitrary();
        } else {
            DeserBean<? super Object> deserBean = getDeserializableBean(type, context);
            if (deserBean.simpleBean) {
                return new SimpleObjectDeserializer(ignoreUnknown, deserBean);
            }
            return new SpecificObjectDeserializer(ignoreUnknown, deserBean);

        }
    }

    @Override
    public <T> DeserBean<T> getDeserializableBean(Argument<T> type, DecoderContext decoderContext) {
        TypeKey key = new TypeKey(type);
        DeserBean<T> deserBeanSupplier = (DeserBean) deserBeanMap.get(key);
        if (deserBeanSupplier == null) {
            deserBeanSupplier = createDeserBean(type, decoderContext);
            deserBeanMap.put(key, (DeserBean) deserBeanSupplier);
        }
        return deserBeanSupplier;
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
                    return new SubtypedDeserBean(annotationMetadata, deserializableIntrospection, decoderContext, this) {
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
                    return new DeserBean(deserializableIntrospection, decoderContext, this) {
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
