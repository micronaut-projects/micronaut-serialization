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
package io.micronaut.serde.beans;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;

public class SubtypedDeserBean<T> extends DeserBean<T> {
    @NonNull
    public final Map<String, DeserBean<? extends T>> subtypes;
    @NonNull
    public final SerdeConfig.Subtyped.DiscriminatorType discriminatorType;
    @NonNull
    public final SerdeConfig.Subtyped.DiscriminatorValue discriminatorValue;
    @NonNull
    public final String discriminatorName;

    public SubtypedDeserBean(BeanIntrospection<T> introspection,
                             Deserializer.DecoderContext decoderContext)
            throws SerdeException {
        super(introspection, decoderContext);
        this.discriminatorType = introspection.enumValue(
                SerdeConfig.Subtyped.class,
                SerdeConfig.Subtyped.DISCRIMINATOR_TYPE,
                SerdeConfig.Subtyped.DiscriminatorType.class
        ).orElse(SerdeConfig.Subtyped.DiscriminatorType.PROPERTY);
        this.discriminatorValue = introspection.enumValue(
                SerdeConfig.Subtyped.class,
                SerdeConfig.Subtyped.DISCRIMINATOR_VALUE,
                SerdeConfig.Subtyped.DiscriminatorValue.class
        ).orElse(SerdeConfig.Subtyped.DiscriminatorValue.CLASS);
        this.discriminatorName = introspection.stringValue(
                SerdeConfig.Subtyped.class,
                SerdeConfig.Subtyped.DISCRIMINATOR_PROP
        ).orElse(discriminatorValue == SerdeConfig.Subtyped.DiscriminatorValue.CLASS ? "@class" : "@type");

        final Class<T> superType = introspection.getBeanType();
        final Collection<BeanIntrospection<? extends T>> subtypeIntrospections = decoderContext.getDeserializableSubtypes(
                superType);
        this.subtypes = new HashMap<>(subtypeIntrospections.size());
        for (BeanIntrospection<? extends T> subtypeIntrospection : subtypeIntrospections) {
            if (discriminatorValue == SerdeConfig.Subtyped.DiscriminatorValue.CLASS) {
                final DeserBean<? extends T> deserBean = decoderContext.getDeserializableBean(
                        Argument.of(subtypeIntrospection.getBeanType())
                );
                this.subtypes.put(
                        subtypeIntrospection.getBeanType().getName(),
                        deserBean
                );
            } else {
                final DeserBean<? extends T> deserBean = decoderContext.getDeserializableBean(
                        Argument.of(subtypeIntrospection.getBeanType())
                );
                final String discriminatorName = deserBean.introspection.stringValue(SerdeConfig.class,
                                                                                     SerdeConfig.TYPE_NAME)
                        .orElse(deserBean.introspection.getBeanType().getSimpleName());
                this.subtypes.put(
                        discriminatorName,
                        deserBean
                );
            }
        }
    }
}
