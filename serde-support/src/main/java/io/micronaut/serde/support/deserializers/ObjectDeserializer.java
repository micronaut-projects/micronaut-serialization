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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.SerdeIntrospections;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.util.SerdeArgumentConf;
import io.micronaut.serde.util.CustomizableDeserializer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Implementation for deserialization of objects that uses introspection metadata.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Internal
public class ObjectDeserializer implements CustomizableDeserializer<Object>, DeserBeanRegistry {
    private final SerdeIntrospections introspections;
    private final Map<DeserBeanKey, Supplier<DeserBean<?>>> deserBeanMap = new ConcurrentHashMap<>(50);
    private final DeserializationConfiguration deserializationConfiguration;
    private final SerdeConfiguration serdeConfiguration;
    @Nullable
    private final SerdeDeserializationPreInstantiateCallback preInstantiateCallback;

    private final ReentrantLock lock = new ReentrantLock();

    public ObjectDeserializer(SerdeIntrospections introspections,
                              DeserializationConfiguration deserializationConfiguration,
                              SerdeConfiguration serdeConfiguration,
                              @Nullable SerdeDeserializationPreInstantiateCallback preInstantiateCallback) {
        this.introspections = introspections;
        this.deserializationConfiguration = deserializationConfiguration;
        this.serdeConfiguration = serdeConfiguration;
        this.preInstantiateCallback = preInstantiateCallback;
    }

    /**
     *
     * @param introspections
     * @param deserializationConfiguration
     * @param preInstantiateCallback
     */
    @Deprecated
    public ObjectDeserializer(SerdeIntrospections introspections,
                              DeserializationConfiguration deserializationConfiguration,
                              @Nullable SerdeDeserializationPreInstantiateCallback preInstantiateCallback) {
        this.introspections = introspections;
        this.deserializationConfiguration = deserializationConfiguration;
        this.serdeConfiguration = null;
        this.preInstantiateCallback = preInstantiateCallback;
    }

    @Override
    public Deserializer<Object> createSpecific(DecoderContext context, Argument<? super Object> type) throws SerdeException {
        if (type.equalsType(Argument.OBJECT_ARGUMENT)) {
            // fallback to dynamic resolution
            return (Decoder decoder, DecoderContext context1, Argument<? super Object> type1) -> decoder.decodeArbitrary();
        }
        DeserBean<? super Object> deserBean = getDeserializableBean(type, context);

        if (deserBean.subtypeInfo != null) {
            DeserializeSubtypeInfo<? super Object> subtypeInfo = deserBean.subtypeInfo;
            SerdeConfig.SerSubtyped.DiscriminatorType discriminatorType = subtypeInfo.info().discriminatorType();
            Map<String, Deserializer<Object>> subtypeDeserializers = CollectionUtils.newHashMap(subtypeInfo.subtypes().size());
            boolean disallowUnwrap = discriminatorType == SerdeConfig.SerSubtyped.DiscriminatorType.WRAPPER_OBJECT;
            for (Map.Entry<String, DeserBean<?>> e : subtypeInfo.subtypes().entrySet()) {
                subtypeDeserializers.put(
                    e.getKey(),
                    findDeserializer(context.getDeserializationConfiguration().orElse(deserializationConfiguration), (DeserBean<? super Object>) e.getValue(), disallowUnwrap)
                );
            }
            Deserializer<Object> supertypeDeserializer = findDeserializer(context.getDeserializationConfiguration().orElse(deserializationConfiguration), deserBean, false);
            return switch (discriminatorType) {
                case WRAPPER_OBJECT -> new WrappedObjectSubtypedDeserializer(
                    subtypeDeserializers,
                    deserBean.ignoreUnknown
                );
                case WRAPPER_ARRAY -> new WrappedArraySubtypedDeserializer(
                    subtypeDeserializers,
                    deserBean.ignoreUnknown
                );
                case PROPERTY, EXISTING_PROPERTY -> new SubtypedPropertyObjectDeserializer(
                    deserBean,
                    subtypeDeserializers,
                    supertypeDeserializer,
                    subtypeInfo.info().discriminatorVisible()
                );
                case EXTERNAL_PROPERTY -> new SubtypedExternalPropertyObjectDeserializer(subtypeInfo, subtypeDeserializers);
            };
        }

        return findDeserializer(context.getDeserializationConfiguration().orElse(deserializationConfiguration), deserBean, false);
    }

    private Deserializer<Object> findDeserializer(DeserializationConfiguration deserializationConfiguration, DeserBean<? super Object> deserBean, boolean disallowUnwrap) {
        Deserializer<Object> deserializer;
        if (deserBean.simpleBean) {
            deserializer = new SimpleObjectDeserializer(deserializationConfiguration.isStrictNullable(), deserBean, preInstantiateCallback);
        } else if (deserBean.recordLikeBean) {
            deserializer = new SimpleRecordLikeObjectDeserializer(deserializationConfiguration.isStrictNullable(), deserBean, preInstantiateCallback);
        } else if (deserBean.delegating) {
            deserializer = new DelegatingObjectDeserializer(deserializationConfiguration.isStrictNullable(), deserBean, preInstantiateCallback);
        } else if (deserBean.isJsonValueProperty) {
            deserializer = new JsonValueDeserializer(deserBean);
        } else {
            deserializer = new SpecificObjectDeserializer(deserializationConfiguration.isStrictNullable(), deserBean, preInstantiateCallback);
        }
        if (!disallowUnwrap && deserBean.wrapperProperty != null) {
            deserializer = new WrappedObjectDeserializer(
                deserializer,
                deserBean.wrapperProperty,
                deserBean.ignoreUnknown
            );
        }
        return deserializer;
    }

    @Override
    public <T> DeserBean<T> getDeserializableBean(Argument<T> type, DecoderContext decoderContext) throws SerdeException {
        SerdeArgumentConf serdeArgumentConf = type.getAnnotationMetadata().isEmpty() ?
            null : new SerdeArgumentConf(type.getAnnotationMetadata());
        DeserBeanKey key = new DeserBeanKey(
            decoderContext.getSerdeConfiguration().orElse(serdeConfiguration),
            decoderContext.getDeserializationConfiguration().orElse(deserializationConfiguration),
            type,
            serdeArgumentConf
        );
        // Use suppliers to prevent recursive update because the lambda can call the same method again
        Supplier<DeserBean<?>> deserBeanSupplier = deserBeanMap.computeIfAbsent(key, ignore -> SupplierUtil.memoizedNonEmpty(() -> createDeserBean(type, serdeArgumentConf, decoderContext)));
        DeserBean<?> deserBean = deserBeanSupplier.get();
        deserBean.initialize(lock, decoderContext);
        return (DeserBean<T>) deserBean;
    }

    private <T> DeserBean<T> createDeserBean(Argument<T> type,
                                             @Nullable SerdeArgumentConf serdeArgumentConf,
                                             DecoderContext decoderContext) {
        try {
            final BeanIntrospection<T> deserializableIntrospection = introspections.getDeserializableIntrospection(type);
            return new DeserBean<>(deserializationConfiguration, type, deserializableIntrospection, decoderContext, this, serdeArgumentConf);
        } catch (SerdeException e) {
            throw new IntrospectionException("Error creating deserializer for type [" + type + "]: " + e.getMessage(), e);
        }
    }
}
