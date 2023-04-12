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
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static io.micronaut.serde.config.annotation.SerdeConfig.SerSubtyped.DiscriminatorValueKind.CLASS_NAME;

/**
 * Models subtype deserialization.
 *
 * @param <T> The generic type
 */
@Internal
class SubtypedDeserBean<T> extends DeserBean<T> {
    // CHECKSTYLE:OFF
    @NonNull
    public final Map<String, DeserBean<? extends T>> subtypes;
    @NonNull
    public final SerdeConfig.SerSubtyped.DiscriminatorType discriminatorType;
    @NonNull
    public final SerdeConfig.SerSubtyped.DiscriminatorValueKind discriminatorValue;
    @NonNull
    public final String discriminatorName;

    @Nullable
    public final String defaultImpl;
    // CHECKSTYLE:ON

    SubtypedDeserBean(AnnotationMetadata annotationMetadata,
                      BeanIntrospection<T> introspection,
                      Deserializer.DecoderContext decoderContext,
                      DeserBeanRegistry deserBeanRegistry) throws SerdeException {
        super(introspection, decoderContext, deserBeanRegistry);
        this.discriminatorType = annotationMetadata.enumValue(
                SerdeConfig.SerSubtyped.class,
                SerdeConfig.SerSubtyped.DISCRIMINATOR_TYPE,
                SerdeConfig.SerSubtyped.DiscriminatorType.class
        ).orElse(SerdeConfig.SerSubtyped.DiscriminatorType.PROPERTY);
        this.discriminatorValue = annotationMetadata.enumValue(
                SerdeConfig.SerSubtyped.class,
                SerdeConfig.SerSubtyped.DISCRIMINATOR_VALUE,
                SerdeConfig.SerSubtyped.DiscriminatorValueKind.class
        ).orElse(CLASS_NAME);
        this.discriminatorName = annotationMetadata.stringValue(
                SerdeConfig.SerSubtyped.class,
                SerdeConfig.SerSubtyped.DISCRIMINATOR_PROP
        ).orElse(discriminatorValue == CLASS_NAME ? "@class" : "@type");

        final Class<T> superType = introspection.getBeanType();
        final Collection<BeanIntrospection<? extends T>> subtypeIntrospections =
            decoderContext.getDeserializableSubtypes(superType);
        this.subtypes = new HashMap<>(subtypeIntrospections.size());
        Class<?> defaultType = annotationMetadata.classValue(DefaultImplementation.class).orElse(null);
        String defaultDiscriminator = null;
        for (BeanIntrospection<? extends T> subtypeIntrospection : subtypeIntrospections) {
            Class<? extends T> subBeanType = subtypeIntrospection.getBeanType();
            final DeserBean<? extends T> deserBean = deserBeanRegistry.getDeserializableBean(
                    Argument.of(subBeanType),
                    decoderContext
            );
            final String discriminatorName;
            if (discriminatorValue == SerdeConfig.SerSubtyped.DiscriminatorValueKind.CLASS_NAME) {
                discriminatorName = subBeanType.getName();
            } else if (discriminatorValue == SerdeConfig.SerSubtyped.DiscriminatorValueKind.CLASS_SIMPLE_NAME) {
                discriminatorName = subBeanType.getSimpleName();
            } else {
                discriminatorName = deserBean.introspection.stringValue(SerdeConfig.class, SerdeConfig.TYPE_NAME)
                        .orElse(deserBean.introspection.getBeanType().getSimpleName());
            }
            this.subtypes.put(
                discriminatorName,
                deserBean
            );
            if (defaultType != null && defaultType.equals(subBeanType)) {
                defaultDiscriminator = discriminatorName;
            }

            String[] names = subtypeIntrospection.stringValues(SerdeConfig.class, SerdeConfig.TYPE_NAMES);
            for (String name: names) {
                this.subtypes.put(name, deserBean);
            }
        }
        this.defaultImpl = defaultDiscriminator;
    }

    @Override
    public boolean isSubtyped() {
        return true;
    }
}
