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
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.util.BeanDefKey;
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
    private final Map<BeanDefKey, Supplier<DeserBean<?>>> deserBeanMap = new ConcurrentHashMap<>(50);
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

        if (deserBean.subtypeInfo != null) {
            DeserBean.SubtypeInfo<? super Object> subtypeInfo = deserBean.subtypeInfo;
            Map<String, Deserializer<Object>> subtypeDeserializers = CollectionUtils.newHashMap(subtypeInfo.subtypes().size());
            for (Map.Entry<String, DeserBean<?>> e : subtypeInfo.subtypes().entrySet()) {
                subtypeDeserializers.put(
                    e.getKey(),
                    findDeserializer((DeserBean<? super Object>) e.getValue(), true)
                );
            }
            Deserializer<Object> supertypeDeserializer = findDeserializer(deserBean, false);
            if (subtypeInfo.discriminatorType() == SerdeConfig.SerSubtyped.DiscriminatorType.WRAPPER_OBJECT) {
                return new WrappedObjectSubtypedDeserializer(
                    subtypeDeserializers,
                    deserBean.ignoreUnknown
                );
            }
            if (subtypeInfo.discriminatorType() == SerdeConfig.SerSubtyped.DiscriminatorType.PROPERTY) {
                return new SubtypedPropertyObjectDeserializer(deserBean, subtypeDeserializers, supertypeDeserializer);
            }
            throw new IllegalStateException("Unrecognized discriminator type: " + subtypeInfo.discriminatorType());
        }

        return findDeserializer(deserBean, false);
    }

    private Deserializer<Object> findDeserializer(DeserBean<? super Object> deserBean, boolean isSubtype) {
        Deserializer<Object> deserializer;
        if (deserBean.simpleBean) {
            deserializer = new SimpleObjectDeserializer(ignoreUnknown, strictNullable, deserBean, preInstantiateCallback);
        } else if (deserBean.recordLikeBean) {
            deserializer = new SimpleRecordLikeObjectDeserializer(ignoreUnknown, strictNullable, deserBean, preInstantiateCallback);
        } else if (deserBean.delegating) {
            deserializer = new DelegatingObjectDeserializer(strictNullable, deserBean, preInstantiateCallback);
        } else {
            deserializer = new SpecificObjectDeserializer(ignoreUnknown, strictNullable, deserBean, preInstantiateCallback);
        }
        if (!isSubtype && deserBean.wrapperProperty != null) {
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
        return gettDeserBean(type, null, null, decoderContext);
    }

    @Override
    public <T> DeserBean<T> getWrappedDeserializableBean(Argument<T> type,
                                                         @Nullable String namePrefix,
                                                         @Nullable String nameSuffix,
                                                         DecoderContext decoderContext) throws SerdeException {
        return gettDeserBean(type, namePrefix, nameSuffix, decoderContext);
    }

    private <T> DeserBean<T> gettDeserBean(Argument<T> type,
                                           @Nullable String namePrefix,
                                           @Nullable String nameSuffix,
                                           DecoderContext decoderContext) throws SerdeException {
        BeanDefKey key = new BeanDefKey(type, namePrefix, nameSuffix);
        // Use suppliers to prevent recursive update because the lambda will can call the same method again
        Supplier<DeserBean<?>> deserBeanSupplier = deserBeanMap.computeIfAbsent(key, ignore -> SupplierUtil.memoizedNonEmpty(() -> createDeserBean(type, namePrefix, nameSuffix, decoderContext)));
        DeserBean<?> deserBean = deserBeanSupplier.get();
        deserBean.initialize(decoderContext);
        return (DeserBean<T>) deserBean;
    }

    private <T> DeserBean<T> createDeserBean(Argument<T> type,
                                             @Nullable String namePrefix,
                                             @Nullable String nameSuffix,
                                             DecoderContext decoderContext) {
        try {
            final BeanIntrospection<T> deserializableIntrospection = introspections.getDeserializableIntrospection(type);
            return new DeserBean<>(type, deserializableIntrospection, decoderContext, this, namePrefix, nameSuffix);
        } catch (SerdeException e) {
            throw new IntrospectionException("Error creating deserializer for type [" + type + "]: " + e.getMessage(), e);
        }
    }
}
