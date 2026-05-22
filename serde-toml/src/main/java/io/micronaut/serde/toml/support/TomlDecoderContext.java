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
package io.micronaut.serde.toml.support;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.reference.PropertyReference;
import io.micronaut.serde.toml.serde.TomlArbitraryValueDeserializer;
import io.micronaut.serde.toml.serde.TomlNullCoercingDeserializer;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;

/**
 * TOML-local decoder context overrides. we delegate all to the default decoder context only with findDeserializer() we have TomlNullCoercingDeserializer so '' is either null or empty string
 * for non strings Integer, Object, etc.
 */
@Internal
public final class TomlDecoderContext implements Deserializer.DecoderContext {
    private final Deserializer.DecoderContext delegate;
    private final LimitingStream.RemainingLimits limits;
    @Nullable
    private final SerdeTomlConfiguration tomlConfiguration;

    public TomlDecoderContext(SerdeRegistry registry,
                              @Nullable Class<?> view,
                              LimitingStream.RemainingLimits limits,
                              @Nullable SerdeTomlConfiguration tomlConfiguration) {
        this(registry.newDecoderContext(view), limits, tomlConfiguration);
    }

    public TomlDecoderContext(Deserializer.DecoderContext delegate,
                              LimitingStream.RemainingLimits limits,
                              @Nullable SerdeTomlConfiguration tomlConfiguration) {
        this.delegate = delegate;
        this.limits = limits;
        this.tomlConfiguration = tomlConfiguration;
    }

    @Override
    public @NonNull <T, D extends Deserializer<? extends T>> D findCustomDeserializer(@NonNull Class<? extends D> deserializerClass) throws SerdeException {
        return delegate.findCustomDeserializer(deserializerClass);
    }

    @Override
    public @NonNull <T> Deserializer<? extends T> findDeserializer(@NonNull Argument<? extends T> type) throws SerdeException {
        Deserializer<? extends T> deserializer = delegate.findDeserializer(type);
        deserializer = TomlNullCoercingDeserializer.wrap(deserializer, type, limits);
        return TomlArbitraryValueDeserializer.wrap(deserializer, type, limits, tomlConfiguration);
    }

    @Override
    public <T> Collection<io.micronaut.core.beans.BeanIntrospection<? extends T>> getDeserializableSubtypes(Class<T> superType) {
        return delegate.getDeserializableSubtypes(superType);
    }

    @Override
    public @NonNull <D extends PropertyNamingStrategy> D findNamingStrategy(@NonNull Class<? extends D> namingStrategyClass) throws SerdeException {
        return delegate.findNamingStrategy(namingStrategyClass);
    }

    @Override
    public <B, P> void pushManagedRef(@NonNull PropertyReference<B, P> reference) {
        delegate.pushManagedRef(reference);
    }

    @Override
    public void popManagedRef() {
        delegate.popManagedRef();
    }

    @Override
    public boolean hasView(Class<?>... views) {
        return delegate.hasView(views);
    }

    @Override
    public @Nullable <B, P> PropertyReference<B, P> resolveReference(@NonNull PropertyReference<B, P> reference) {
        return delegate.resolveReference(reference);
    }

    @Override
    public @NonNull Optional<SerdeConfiguration> getSerdeConfiguration() {
        return delegate.getSerdeConfiguration();
    }

    @Override
    public @NonNull Optional<DeserializationConfiguration> getDeserializationConfiguration() {
        return delegate.getDeserializationConfiguration();
    }
}
