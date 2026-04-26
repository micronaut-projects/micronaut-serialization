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
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.SerializationConfiguration;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.reference.PropertyReference;
import io.micronaut.serde.reference.SerializationReference;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * TOML-local encoder context overrides.
 */
@Internal
public final class TomlEncoderContext implements Serializer.EncoderContext {
    private final Serializer.EncoderContext delegate;

    public TomlEncoderContext(SerdeRegistry registry, @Nullable Class<?> view) {
        this(registry.newEncoderContext(view));
    }

    public TomlEncoderContext(Serializer.EncoderContext delegate) {
        this.delegate = delegate;
    }

    @Override
    public @NonNull <T, D extends Serializer<? extends T>> D findCustomSerializer(@NonNull Class<? extends D> serializerClass) throws SerdeException {
        return delegate.findCustomSerializer(serializerClass);
    }

    @Override
    public @NonNull <T> Serializer<? super T> findSerializer(@NonNull Argument<? extends T> forType) throws SerdeException {
        return delegate.findSerializer(forType);
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
    public @Nullable <B, P> SerializationReference<B, P> resolveReference(@NonNull SerializationReference<B, P> reference) {
        return delegate.resolveReference(reference);
    }

    @Override
    public @NonNull Optional<SerdeConfiguration> getSerdeConfiguration() {
        return delegate.getSerdeConfiguration().map(TomlSerdeConfiguration::new);
    }

    @Override
    public @NonNull Optional<SerializationConfiguration> getSerializationConfiguration() {
        return delegate.getSerializationConfiguration().map(TomlSerializationConfiguration::new);
    }
}
