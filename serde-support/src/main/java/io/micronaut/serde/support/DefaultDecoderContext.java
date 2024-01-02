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
package io.micronaut.serde.support;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.reference.AbstractPropertyReferenceManager;
import io.micronaut.serde.reference.PropertyReference;

import java.util.Collection;

/**
 * Default implementation of {@link io.micronaut.serde.Deserializer.DecoderContext}.
 *
 * @since 1.0.0
 */
@Internal
class DefaultDecoderContext extends AbstractPropertyReferenceManager implements Deserializer.DecoderContext {
    private final DefaultSerdeRegistry registry;

    DefaultDecoderContext(DefaultSerdeRegistry registry) {
        this.registry = registry;
    }

    @Override
    public final ConversionService getConversionService() {
        return registry.getConversionService();
    }

    @Override
    public final <T, D extends Deserializer<? extends T>> D findCustomDeserializer(Class<? extends D> deserializerClass)
            throws SerdeException {
        return registry.findCustomDeserializer(deserializerClass);
    }

    @Override
    public final <T> Deserializer<? extends T> findDeserializer(Argument<? extends T> type) throws SerdeException {
        return registry.findDeserializer(type);
    }

    @Override
    public <D extends PropertyNamingStrategy> D findNamingStrategy(Class<? extends D> namingStrategyClass) throws SerdeException {
        return registry.findNamingStrategy(namingStrategyClass);
    }

    @Override
    public final <T> Collection<BeanIntrospection<? extends T>> getDeserializableSubtypes(Class<T> superType) {
        return registry.getDeserializableSubtypes(superType);
    }

    @Override
    public <B, P> PropertyReference<B, P> resolveReference(PropertyReference<B, P> reference) {
        if (refs != null) {
            for (PropertyReference<?, ?> ref : refs) {
                if (ref.getReferenceName().equals(reference.getProperty().getName())) {
                    final Object o = ref.getReference();
                    if (o != null) {
                        //noinspection unchecked
                        return (PropertyReference<B, P>) ref;
                    }
                }
            }
        }
        return reference;
    }

    @Override
    public SerdeConfiguration getSerdeConfiguration() {
        return registry.getSerdeConfiguration();
    }

    @Override
    public DeserializationConfiguration getDeserializationConfiguration() {
        return registry.getDeserializationConfiguration();
    }
}
