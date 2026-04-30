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
import org.jspecify.annotations.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
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
 * @param beanType            The beanType
 * @param subtypes            The subtypes
 * @param info                The subtype info
 * @param defaultType         The default type
 * @param <T>                 The bean type
 * @author Denis Stepanov
 */
@Internal
record DeserBeanSubtypeInfo<T>(
    Class<T> beanType,
    Map<String, SubtypeDef<T>> subtypes,
    SubtypeInfo info,
    @Nullable
    SubtypeDef<T> defaultType
) {

    /**
     * Find the {@link DeserBean} for discriminator of not provided one.
     *
     * @param discriminatorValue The discriminator value
     * @return The {@link DeserBean}
     */
    DeserBean<? extends T> findDeserBean(@Nullable String discriminatorValue) throws SerdeException {
        SubtypeDef<T> subtypeDef;
        if (discriminatorValue == null) {
            subtypeDef = defaultType;
        } else {
            subtypeDef = subtypes.getOrDefault(discriminatorValue, defaultType);
        }
        if (subtypeDef == null) {
            throw unknownSuperTypeException();
        }
        DeserBean<? extends T> deserBean = subtypeDef.deserBean;
        if (deserBean == null) {
            throw unknownSuperTypeException();
        }
        return deserBean;
    }

    /**
     * @return Creates unknown supertype exception
     */
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
        Map<String, SubtypeDef<T>> subtypes = CollectionUtils.newHashMap(subtypeIntrospections.size());
        Class<? extends T> defaultType = introspection.classValue(DefaultImplementation.class)
            .orElseGet(() -> introspection.classValue(SerdeConfig.SerSubtyped.DEFAULT_IMPL).orElse(null));
        if (defaultType == null && !deserializationConfiguration.isSubtypesRequireDefaultImpl()) {
            // Jackson always requires to use `defaultImpl` but our initial implementation always takes the supertype
            defaultType = introspection.getBeanType();
        }
        SubtypeDef<T> defaultDeserType = null;
        if (defaultType != null) {
            if ("com.fasterxml.jackson.annotation.JsonTypeInfo".equals(introspection.stringValue(SerdeConfig.SerSubtyped.class, SerdeConfig.SerSubtyped.DEFAULT_IMPL).orElse(null))) {
                defaultType = null;
            }
        }
        if (introspection.getBeanType().equals(defaultType)) {
            defaultDeserType = new SubtypeDef<>(superTypeDeserBean, introspection.asArgument());
        }
        for (BeanIntrospection<? extends T> subtypeIntrospection : subtypeIntrospections) {
            Class<? extends T> subBeanType = subtypeIntrospection.getBeanType();
            Argument<? extends T> argument = subtypeIntrospection.asArgument();
            boolean hasNotResolvedGenerics = false;
            for (BeanProperty<? extends T, Object> beanProperty : subtypeIntrospection.getBeanProperties()) {
                Argument<Object> argument1 = beanProperty.asArgument();
                hasNotResolvedGenerics |= argument1.isTypeVariable();
            }

            SubtypeDef<T> subtypeDef;
            if (hasNotResolvedGenerics) {
                subtypeDef = new SubtypeDef<>(null, argument);
            } else {
                final DeserBean<? extends T> deserBean = deserBeanRegistry.getDeserializableBean(
                    argument,
                    null,
                    decoderContext
                );
                subtypeDef = new SubtypeDef<>(deserBean, argument);
            }
            if (defaultDeserType == null && defaultType != null && defaultType.equals(subBeanType)) {
                defaultDeserType = subtypeDef;
            }
            if (subtypeInfo.deduct()) {
                subtypes.put(subBeanType.getName(), subtypeDef);
            } else {
                String[] types = subtypeInfo.subtypes().get(subBeanType);
                if (types != null) {
                    for (String type : types) {
                        subtypes.put(type, subtypeDef);
                    }
                }

                subtypeIntrospection.stringValue(SerdeConfig.class, SerdeConfig.TYPE_NAME).ifPresent(name -> subtypes.put(name, subtypeDef));
                String[] names = subtypeIntrospection.stringValues(SerdeConfig.class, SerdeConfig.TYPE_NAMES);
                for (String name : names) {
                    subtypes.put(name, subtypeDef);
                }
            }
        }
        if (defaultDeserType == null && defaultType != null && !subtypeIntrospections.isEmpty()) {
            if (defaultType == introspection.getBeanType()) {
                defaultDeserType = new SubtypeDef<>(superTypeDeserBean, introspection.asArgument());
            } else {
                Argument<? extends T> argument = Argument.of(defaultType);
                defaultDeserType = new SubtypeDef<>(
                    deserBeanRegistry.getDeserializableBean(argument, null, decoderContext),
                    argument
                );
            }
        }
        return new DeserBeanSubtypeInfo<>(
            introspection.getBeanType(),
            subtypes,
            subtypeInfo,
            defaultDeserType
        );
    }

    record SubtypeDef<K>(@Nullable DeserBean<? extends K> deserBean, Argument<? extends K> type) {
    }

}
