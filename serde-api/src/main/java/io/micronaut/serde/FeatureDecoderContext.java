/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.serde;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.reference.PropertyReference;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * Decoder context with overridden deserialization features.
 *
 * @since 3.0
 */
@Internal
final class FeatureDecoderContext implements Deserializer.DecoderContext {
    private final Deserializer.DecoderContext delegate;
    private final Set<DeserializationConfiguration.Feature> features;

    FeatureDecoderContext(Deserializer.DecoderContext delegate, Set<DeserializationConfiguration.Feature> features) {
        this.delegate = delegate;
        this.features = features;
    }

    @Override
    public ConversionService getConversionService() {
        return delegate.getConversionService();
    }

    @Override
    public boolean hasView(Class<?>... views) {
        return delegate.hasView(views);
    }

    @Override
    @Nullable
    public <B, P> PropertyReference<B, P> resolveReference(PropertyReference<B, P> reference) {
        return delegate.resolveReference(reference);
    }

    @Override
    public Optional<SerdeConfiguration> getSerdeConfiguration() {
        return delegate.getSerdeConfiguration();
    }

    @Override
    public Optional<DeserializationConfiguration> getDeserializationConfiguration() {
        return delegate.getDeserializationConfiguration();
    }

    @Override
    public Set<DeserializationConfiguration.Feature> getFeatures() {
        return features;
    }

    @Override
    public <T, D extends Deserializer<? extends T>> D findCustomDeserializer(Class<? extends D> deserializerClass) throws SerdeException {
        return delegate.findCustomDeserializer(deserializerClass);
    }

    @Override
    public <T> Deserializer<? extends T> findDeserializer(Argument<? extends T> type) throws SerdeException {
        return delegate.findDeserializer(type);
    }

    @Override
    public <T> Collection<BeanIntrospection<? extends T>> getDeserializableSubtypes(Class<T> superType) {
        return delegate.getDeserializableSubtypes(superType);
    }

    @Override
    public <D extends PropertyNamingStrategy> D findNamingStrategy(Class<? extends D> namingStrategyClass) throws SerdeException {
        return delegate.findNamingStrategy(namingStrategyClass);
    }

    @Override
    public <B, P> void pushManagedRef(PropertyReference<B, P> reference) {
        delegate.pushManagedRef(reference);
    }

    @Override
    public void popManagedRef() {
        delegate.popManagedRef();
    }
}
