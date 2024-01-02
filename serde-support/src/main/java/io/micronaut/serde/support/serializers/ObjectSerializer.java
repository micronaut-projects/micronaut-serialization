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
package io.micronaut.serde.support.serializers;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Primary;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.GenericPlaceholder;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.serde.SerdeIntrospections;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.util.SerdeArgumentConf;
import io.micronaut.serde.support.util.SubtypeInfo;
import io.micronaut.serde.util.CustomizableSerializer;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Fallback {@link io.micronaut.serde.Serializer} for general {@link Object} values. For deserialization, deserializes to
 * standard types
 * like {@link Number}, {@link String}, {@link Boolean}, {@link Map} and {@link java.util.List}.
 * <p>
 * This class is used in multiple scenarios:
 * <ul>
 *     <li>When the user has an {@link Object} property in a serializable bean.</li>
 *     <li>When the user explicitly calls {@link io.micronaut.json.JsonMapper#writeValue}{@code (gen, }{@link Object}{@code
 *     .class)}</li>
 * </ul>
 */
@Internal
@Singleton
@Primary
@BootstrapContextCompatible
public final class ObjectSerializer implements CustomizableSerializer<Object> {
    private final SerdeIntrospections introspections;
    private final BeanContext beanContext;
    private final Map<SerBeanKey, Supplier<SerBean<?>>> serBeanMap = new ConcurrentHashMap<>(50);

    public ObjectSerializer(SerdeIntrospections introspections, BeanContext beanContext) {
        this.introspections = introspections;
        this.beanContext = beanContext;
    }

    @Override
    public io.micronaut.serde.Serializer<Object> createSpecific(@NonNull EncoderContext encoderContext, Argument<?> type) throws SerdeException {
        boolean isObjectType = type.equalsType(Argument.OBJECT_ARGUMENT);
        if (isObjectType || type instanceof GenericPlaceholder) {
            // dynamic type resolving
            Serializer<Object> outer = !isObjectType ? createSpecificInternal(encoderContext, type) : null;
            return new RuntimeTypeSerializer(encoderContext, outer, type);
        } else {
            return createSpecificInternal(encoderContext, type);
        }
    }

    private io.micronaut.serde.Serializer<Object> createSpecificInternal(EncoderContext encoderContext,
                                                                         Argument<?> type) throws SerdeException {
        SerBean<Object> serBean;
        try {
            serBean = (SerBean<Object>) getSerializableBean(type, encoderContext);
        } catch (IntrospectionException e) {
            // no introspection, create dynamic serialization case
            return new RuntimeTypeSerializer(encoderContext, e, type);
        }

        io.micronaut.serde.Serializer<Object> serializer;
        if (serBean.simpleBean) {
            serializer = new SimpleObjectSerializer<>(serBean);
        } else if (serBean.jsonValue != null) {
            serializer = new JsonValueSerializer<>(serBean.jsonValue);
        } else {
            serializer = new CustomizedObjectSerializer<>(serBean);
        }
        if (serBean.subtyped) {
            serializer = new RuntimeTypeSerializer(encoderContext, serializer, type);
        } else {
            if (serBean.wrapperProperty != null) {
                serializer = new WrappedObjectSerializer<>(serializer, serBean.wrapperProperty);
            } else if (serBean.arrayWrapperProperty != null) {
                serializer = new WrappedArraySerializer<>(serializer, serBean.arrayWrapperProperty);
            } else {
                SubtypeInfo subtypeInfo = serBean.subtypeInfo;
                if (subtypeInfo != null) {
                    if (subtypeInfo.discriminatorType() == SerdeConfig.SerSubtyped.DiscriminatorType.WRAPPER_OBJECT) {
                        String[] names = subtypeInfo.subtypes().get(type.getType());
                        if (names != null) {
                            serializer = new WrappedObjectSerializer<>(serializer, names[0]);
                        }
                    }
                    if (subtypeInfo.discriminatorType() == SerdeConfig.SerSubtyped.DiscriminatorType.WRAPPER_ARRAY) {
                        String[] names = subtypeInfo.subtypes().get(type.getType());
                        if (names != null) {
                            serializer = new WrappedArraySerializer<>(serializer, names[0]);
                        }
                    }
                }
            }
        }
        return serializer;
    }

    private <T> SerBean<T> getSerializableBean(Argument<T> type,
                                               EncoderContext context) throws SerdeException {
        AnnotationMetadata annotationMetadata = type.getAnnotationMetadata();
        SerdeArgumentConf serdeArgumentConf = annotationMetadata.isEmpty() ? null : new SerdeArgumentConf(annotationMetadata);
        SerBeanKey key = new SerBeanKey(context.getSerdeConfiguration(), context.getSerializationConfiguration(), type, serdeArgumentConf);
        // Use suppliers to prevent recursive update because the lambda will call the same method again
        Supplier<SerBean<?>> serBeanSupplier = serBeanMap.computeIfAbsent(key, ignore -> SupplierUtil.memoizedNonEmpty(() -> {
            try {
                return new SerBean<>(type, introspections, context, serdeArgumentConf, beanContext);
            } catch (SerdeException e) {
                throw new IntrospectionException("Error creating deserializer for type [" + type + "]: " + e.getMessage(), e);
            }
        }));
        SerBean<?> serBean = serBeanSupplier.get();
        serBean.initialize(context);
        return (SerBean<T>) serBean;
    }
}
