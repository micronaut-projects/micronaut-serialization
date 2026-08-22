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
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.SerializationConfiguration;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.reference.PropertyReference;
import io.micronaut.serde.reference.SerializationReference;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.Set;

/**
 * Encoder context with overridden serialization features.
 *
 * @since 3.0
 */
@Internal
final class FeatureEncoderContext implements Serializer.EncoderContext {
    private final Serializer.EncoderContext delegate;
    private final Set<SerializationConfiguration.Feature> features;

    FeatureEncoderContext(Serializer.EncoderContext delegate, Set<SerializationConfiguration.Feature> features) {
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
    public <B, P> SerializationReference<B, P> resolveReference(SerializationReference<B, P> reference) {
        return delegate.resolveReference(reference);
    }

    @Override
    public @Nullable Object resolveObjectId(Object value) {
        return delegate.resolveObjectId(value);
    }

    @Override
    public void registerObjectId(Object value, Object id) {
        delegate.registerObjectId(value, id);
    }

    @Override
    public Optional<SerdeConfiguration> getSerdeConfiguration() {
        return delegate.getSerdeConfiguration();
    }

    @Override
    public Optional<SerializationConfiguration> getSerializationConfiguration() {
        return delegate.getSerializationConfiguration();
    }

    @Override
    public Set<SerializationConfiguration.Feature> getFeatures() {
        return features;
    }

    @Override
    public <T, D extends Serializer<? extends T>> D findCustomSerializer(Class<? extends D> serializerClass) throws SerdeException {
        return delegate.findCustomSerializer(serializerClass);
    }

    @Override
    public <T> Serializer<? super T> findSerializer(Argument<? extends T> forType) throws SerdeException {
        return delegate.findSerializer(forType);
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
