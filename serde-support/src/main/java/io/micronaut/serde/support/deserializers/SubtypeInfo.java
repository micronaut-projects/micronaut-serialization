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
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;

import java.util.Collection;
import java.util.Map;

import static io.micronaut.serde.config.annotation.SerdeConfig.SerSubtyped.DiscriminatorValueKind.CLASS_NAME;

/**
 * The subtype info.
 *
 * @param subtypes             The subtypes
 * @param discriminatorType    The discriminator type
 * @param discriminatorName    The discriminator name
 * @param defaultImpl          The default impl
 * @param discriminatorVisible The discriminator visible
 * @param <T>                  The bean type
 * @author Denis Stepanov
 */
@Internal
record SubtypeInfo<T>(
    @NonNull
    Map<String, DeserBean<? extends T>> subtypes,
    @NonNull
    SerdeConfig.SerSubtyped.DiscriminatorType discriminatorType,
    @NonNull
    String discriminatorName,
    @Nullable
    String defaultImpl,
    boolean discriminatorVisible
) {

    static <T> SubtypeInfo<T> create(AnnotationMetadata annotationMetadata,
                                     BeanIntrospection<T> introspection,
                                     Deserializer.DecoderContext decoderContext,
                                     DeserBeanRegistry deserBeanRegistry) throws SerdeException {

        if (!annotationMetadata.hasAnnotation(SerdeConfig.SerSubtyped.class)) {
            return null;
        }

        SerdeConfig.SerSubtyped.DiscriminatorType discriminatorType = annotationMetadata.enumValue(
            SerdeConfig.SerSubtyped.class,
            SerdeConfig.SerSubtyped.DISCRIMINATOR_TYPE,
            SerdeConfig.SerSubtyped.DiscriminatorType.class
        ).orElse(SerdeConfig.SerSubtyped.DiscriminatorType.PROPERTY);
        SerdeConfig.SerSubtyped.DiscriminatorValueKind discriminatorValue = annotationMetadata.enumValue(
            SerdeConfig.SerSubtyped.class,
            SerdeConfig.SerSubtyped.DISCRIMINATOR_VALUE,
            SerdeConfig.SerSubtyped.DiscriminatorValueKind.class
        ).orElse(CLASS_NAME);
        String discriminatorName = annotationMetadata.stringValue(
            SerdeConfig.SerSubtyped.class,
            SerdeConfig.SerSubtyped.DISCRIMINATOR_PROP
        ).orElse(discriminatorValue == CLASS_NAME ? "@class" : "@type");

        final Class<T> superType = introspection.getBeanType();
        final Collection<BeanIntrospection<? extends T>> subtypeIntrospections =
            decoderContext.getDeserializableSubtypes(superType);
        Map<String, DeserBean<? extends T>> subtypes = CollectionUtils.newHashMap(subtypeIntrospections.size());
        Class<?> defaultType = annotationMetadata.classValue(DefaultImplementation.class).orElse(null);
        String defaultDiscriminator = null;
        for (BeanIntrospection<? extends T> subtypeIntrospection : subtypeIntrospections) {
            Class<? extends T> subBeanType = subtypeIntrospection.getBeanType();
            final DeserBean<? extends T> deserBean = deserBeanRegistry.getDeserializableBean(
                Argument.of(subBeanType),
                decoderContext
            );
            if (defaultType != null && defaultType.equals(subBeanType)) {
                defaultDiscriminator = subtypeIntrospection.stringValue(SerdeConfig.class, SerdeConfig.TYPE_NAME).orElseThrow();
            }

            subtypeIntrospection.stringValue(SerdeConfig.class, SerdeConfig.TYPE_NAME).ifPresent(name -> subtypes.put(name, deserBean));
            String[] names = subtypeIntrospection.stringValues(SerdeConfig.class, SerdeConfig.TYPE_NAMES);
            for (String name : names) {
                subtypes.put(name, deserBean);
            }
        }

        return new SubtypeInfo<>(
            subtypes,
            discriminatorType,
            discriminatorName,
            defaultDiscriminator,
            annotationMetadata.booleanValue(SerdeConfig.SerSubtyped.class, SerdeConfig.SerSubtyped.DISCRIMINATOR_VISIBLE).orElse(false)
        );
    }

}
