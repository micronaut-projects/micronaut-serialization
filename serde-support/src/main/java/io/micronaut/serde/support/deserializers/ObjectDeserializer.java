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
import org.jspecify.annotations.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.SerdeIntrospections;
import io.micronaut.serde.UpdatingDeserializer;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.util.SerdeArgumentConf;
import io.micronaut.serde.support.util.SubtypeInfo;
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

    @Override
    public Deserializer<Object> createSpecific(DecoderContext context, Argument<? super Object> type) throws SerdeException {
        if (type.equalsType(Argument.OBJECT_ARGUMENT)) {
            // fallback to dynamic resolution
            return (Decoder decoder, DecoderContext ignore1, Argument<? super Object> ignore2) -> decoder.decodeArbitrary();
        }
        DeserializationConfiguration deserializationConfiguration = context.getDeserializationConfiguration().orElse(this.deserializationConfiguration);
        DeserBean<? super Object> deserBean = getDeserializableBean(type, null, context);
        if (deserBean.subtypeInfo != null) {
            return createSubtypeDeserializer(context, deserializationConfiguration, deserBean, type);
        }
        return findDeserializer(deserializationConfiguration, deserBean, false);
    }

    private Deserializer<Object> createSubtypeDeserializer(DecoderContext context,
                                                           DeserializationConfiguration deserializationConfiguration,
                                                           DeserBean<? super Object> deserBean,
                                                           Argument<? super Object> type) {
        DeserBeanSubtypeInfo<Object> subtypeInfo = deserBean.subtypeInfo;
        SubtypeInfo info = subtypeInfo.info();
        SerdeConfig.SerSubtyped.DiscriminatorType discriminatorType = info.discriminatorType();
        Map<String, Deserializer<Object>> subtypeDeserializers = CollectionUtils.newHashMap(subtypeInfo.subtypes().size());
        boolean disallowUnwrap = discriminatorType == SerdeConfig.SerSubtyped.DiscriminatorType.WRAPPER_OBJECT;
        DeserializerSubtypeInfo<Object> deserializerSubtypeInfo;
        Deserializer<Object> defaultDeserializer = null;
        boolean hasUnresolved = false;
        for (Map.Entry<String, DeserBeanSubtypeInfo.SubtypeDef<Object>> e : subtypeInfo.subtypes().entrySet()) {
            DeserBeanSubtypeInfo.SubtypeDef<Object> subtypeDef =  e.getValue();
            DeserBean<?> subtypeDeserBean = subtypeDef.deserBean();
            if (subtypeDeserBean == null) {
                hasUnresolved = true;
                continue;
            }
            Deserializer<Object> subtypeDeserializer = findDeserializer(deserializationConfiguration, (DeserBean<? super Object>) subtypeDeserBean, disallowUnwrap);
            subtypeDeserializers.put(
                e.getKey(),
                subtypeDeserializer
            );
            if (defaultDeserializer == null && subtypeInfo.defaultType() == subtypeDef) {
                defaultDeserializer = subtypeDeserializer;
            }
        }
        if (defaultDeserializer == null && subtypeInfo.defaultType() != null) {
            DeserBean<?> defaultDeserBean = subtypeInfo.defaultType().deserBean();
            if (defaultDeserBean != null) {
                defaultDeserializer = findDeserializer(deserializationConfiguration, (DeserBean<? super Object>) defaultDeserBean, disallowUnwrap);
            }
        }
        ResolvedDeserializerSubtypeInfo<Object> resolved = new ResolvedDeserializerSubtypeInfo<>(subtypeInfo, subtypeDeserializers, defaultDeserializer);
        if (!hasUnresolved) {
            deserializerSubtypeInfo = resolved;
        } else {
            Deserializer<Object> finalDefaultDeserializer = defaultDeserializer;
            deserializerSubtypeInfo = new DeserializerSubtypeInfo<>() {
                @Override
                public DeserBeanSubtypeInfo<Object> parent() {
                    return subtypeInfo;
                }

                @Override
                public Deserializer<Object> findDeserializer(String discriminatorValue) throws SerdeException {
                    Deserializer<Object> deserializer = subtypeDeserializers.get(discriminatorValue);
                    if (deserializer != null) {
                        return deserializer;
                    }
                    DeserBeanSubtypeInfo.SubtypeDef<Object> subtype = subtypeInfo.subtypes().get(discriminatorValue);
                    Argument<?> argument;
                    if (subtype != null) {
                        argument = subtype.type();
                    } else {
                        if (finalDefaultDeserializer != null) {
                            return finalDefaultDeserializer;
                        }
                        DeserBeanSubtypeInfo.SubtypeDef<Object> defaultType = subtypeInfo.defaultType();
                        if (defaultType == null) {
                            throw subtypeInfo.unknownSuperTypeException();
                        }
                        argument = defaultType.type();
                    }
                    DeserBean<?> subtypeDeserBean = getDeserializableBean(argument, type.getTypeVariables(), context);
                    return ObjectDeserializer.this.findDeserializer(deserializationConfiguration, (DeserBean<Object>) subtypeDeserBean, disallowUnwrap);
                }
            };
        }
        if (info.deduct()) {
            return new SubtypedDeductionDeserializer(
                deserBean,
                subtypeDeserializers);
        }
        return switch (info.discriminatorType()) {
            case WRAPPER_OBJECT ->
                new WrappedObjectSubtypedDeserializer(deserializerSubtypeInfo, deserBean.ignoreUnknown);
            case WRAPPER_ARRAY ->
                new WrappedArraySubtypedDeserializer(deserializerSubtypeInfo, deserBean.ignoreUnknown);
            case PROPERTY, EXISTING_PROPERTY ->
                new SubtypedPropertyObjectDeserializer(deserializerSubtypeInfo);
            case EXTERNAL_PROPERTY ->
                new SubtypedExternalPropertyObjectDeserializer(deserializerSubtypeInfo);
        };
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
        if (deserializer instanceof UpdatingDeserializer<Object> updatingDeserializer) {
            return new ErrorCatchingUpdatingDeserializer<>(updatingDeserializer);
        }
        return new ErrorCatchingDeserializer<>(deserializer);
    }

    @Override
    public <T> DeserBean<T> getDeserializableBean(Argument<T> type, @Nullable Map<String, Argument<?>> typeArguments, DecoderContext decoderContext) throws SerdeException {
        SerdeArgumentConf serdeArgumentConf = type.getAnnotationMetadata().isEmpty() ?
            null : new SerdeArgumentConf(type.getAnnotationMetadata());
        DeserBeanKey key = new DeserBeanKey(
            decoderContext.getSerdeConfiguration().orElse(serdeConfiguration),
            decoderContext.getDeserializationConfiguration().orElse(deserializationConfiguration),
            type,
            typeArguments,
            serdeArgumentConf
        );
        // Use suppliers to prevent recursive update because the lambda can call the same method again
        Supplier<DeserBean<?>> deserBeanSupplier = deserBeanMap.computeIfAbsent(key, ignore -> SupplierUtil.memoizedNonEmpty(() -> createDeserBean(type, typeArguments, serdeArgumentConf, decoderContext)));
        DeserBean<?> deserBean = deserBeanSupplier.get();
        deserBean.initialize(lock, decoderContext);
        return (DeserBean<T>) deserBean;
    }

    private <T> DeserBean<T> createDeserBean(Argument<T> type,
                                             @Nullable
                                             Map<String, Argument<?>> typeArguments,
                                             @Nullable SerdeArgumentConf serdeArgumentConf,
                                             DecoderContext decoderContext) {
        try {
            final BeanIntrospection<T> deserializableIntrospection = introspections.getDeserializableIntrospection(type);
            Map<String, Argument<?>> ta = typeArguments == null || typeArguments.isEmpty() ? type.getTypeVariables() : typeArguments;
            return new DeserBean<>(deserializationConfiguration, ta, deserializableIntrospection, decoderContext, this, serdeArgumentConf);
        } catch (SerdeException e) {
            throw new IntrospectionException("Error creating deserializer for type [" + type + "]: " + e.getMessage(), e);
        }
    }
}
