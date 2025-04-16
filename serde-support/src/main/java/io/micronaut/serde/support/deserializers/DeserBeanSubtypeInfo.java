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

import io.micronaut.context.annotation.DefaultImplementation;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.util.SubtypeInfo;

import java.util.Collection;
import java.util.Map;

/**
 * The subtype info.
 *
 * @param beanType    The beanType
 * @param subtypes    The subtypes
 * @param info        The subtype info
 * @param defaultType The default type
 * @param <T>         The bean type
 * @author Denis Stepanov
 */
@Internal
record DeserBeanSubtypeInfo<T>(
    Class<T> beanType,
    @NonNull
    Map<String, DeserBean<? extends T>> subtypes,
    SubtypeInfo info,
    @Nullable
    DeserBean<? extends T> defaultType
) {

    /**
     * Find the {@link DeserBean} for discriminator of not provided one.
     *
     * @param discriminatorValue The discriminator value
     * @return The {@link DeserBean}
     */
    @NonNull
    DeserBean<? extends T> findDeserBean(@Nullable String discriminatorValue) throws SerdeException {
        DeserBean<? extends T> deserBean;
        if (discriminatorValue == null) {
            deserBean = defaultType;
        } else {
            deserBean = subtypes.getOrDefault(discriminatorValue, defaultType);
        }
        if (deserBean == null) {
            throw unknownSuperTypeException();
        }
        return deserBean;
    }

    /**
     * @return Creates unknown supertype exception
     */
    @NonNull
    public SerdeException unknownSuperTypeException() {
        return new SerdeException("Could not resolve subtype of ["
            + beanType.getName() + "] missing type id property '" + info.discriminatorName() + "'");
    }

    @Nullable
    static <T> DeserBeanSubtypeInfo<T> create(@Nullable SubtypeInfo subtypeInfo,
                                              BeanIntrospection<T> introspection,
                                              Deserializer.DecoderContext decoderContext,
                                              DeserBean<T> superTypeDeserBean,
                                              DeserializationConfiguration deserializationConfiguration,
                                              DeserBeanRegistry deserBeanRegistry) throws SerdeException {

        if (subtypeInfo == null) {
            return null;
        }

        final Class<T> superType = introspection.getBeanType();
        final Collection<BeanIntrospection<? extends T>> subtypeIntrospections =
            decoderContext.getDeserializableSubtypes(superType);
        Map<String, DeserBean<? extends T>> subtypes = CollectionUtils.newHashMap(subtypeIntrospections.size());
        Class<? extends T> defaultType = introspection.classValue(DefaultImplementation.class)
            .orElseGet(() -> introspection.classValue(SerdeConfig.SerSubtyped.DEFAULT_IMPL).orElse(null));
        if (defaultType == null && !deserializationConfiguration.isAvoidSupertypeSubtype()) {
            // Jackson always requires to use `defaultImpl` but our initial implementation always takes the supertype
            defaultType = introspection.getBeanType();
        }
        DeserBean<? extends T> defaultDeserType = null;
        if (defaultType != null) {
            if ("com.fasterxml.jackson.annotation.JsonTypeInfo".equals(introspection.stringValue(SerdeConfig.SerSubtyped.class, SerdeConfig.SerSubtyped.DEFAULT_IMPL).orElse(null))) {
                defaultType = null;
            }
        }
        if (introspection.getBeanType().equals(defaultType)) {
            defaultDeserType = superTypeDeserBean;
        }
        for (BeanIntrospection<? extends T> subtypeIntrospection : subtypeIntrospections) {
            Class<? extends T> subBeanType = subtypeIntrospection.getBeanType();
            final DeserBean<? extends T> deserBean = deserBeanRegistry.getDeserializableBean(
                Argument.of(subBeanType),
                decoderContext
            );

            String[] types = subtypeInfo.subtypes().get(subBeanType);
            if (types != null) {
                for (String type : types) {
                    subtypes.put(type, deserBean);
                }
            }

            if (defaultDeserType == null && defaultType != null && defaultType.equals(subBeanType)) {
                defaultDeserType = deserBean;
            }

            subtypeIntrospection.stringValue(SerdeConfig.class, SerdeConfig.TYPE_NAME).ifPresent(name -> subtypes.put(name, deserBean));
            String[] names = subtypeIntrospection.stringValues(SerdeConfig.class, SerdeConfig.TYPE_NAMES);
            for (String name : names) {
                subtypes.put(name, deserBean);
            }
        }
        if (defaultDeserType == null && defaultType != null && !subtypeIntrospections.isEmpty()) {
            if (defaultType == introspection.getBeanType()) {
                defaultDeserType = superTypeDeserBean;
            } else {
                defaultDeserType = deserBeanRegistry.getDeserializableBean(Argument.of(defaultType), decoderContext);
            }
        }
        return new DeserBeanSubtypeInfo<>(
            introspection.getBeanType(),
            subtypes,
            subtypeInfo,
            defaultDeserType
        );
    }

}
