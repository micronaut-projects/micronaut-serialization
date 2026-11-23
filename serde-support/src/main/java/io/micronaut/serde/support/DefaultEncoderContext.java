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
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.SerializationConfiguration;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.reference.AbstractPropertyReferenceManager;
import io.micronaut.serde.reference.PropertyReference;
import io.micronaut.serde.reference.SerializationReference;
import io.micronaut.serde.support.reference.DocumentIdSerializationReference;
import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Optional;

/**
 * Default implementation of {@link io.micronaut.serde.Serializer.EncoderContext}.
 *
 * @since 1.0.0
 */
@Internal
class DefaultEncoderContext extends AbstractPropertyReferenceManager implements Serializer.EncoderContext {
    private final DefaultSerdeRegistry registry;
    // Beans written in full in the current document, allocated only when a document uses object identity
    @Nullable
    private IdentityHashMap<Object, Object> writtenBeans;

    DefaultEncoderContext(DefaultSerdeRegistry registry) {
        this.registry = registry;
    }

    @Override
    public ConversionService getConversionService() {
        return registry.getConversionService();
    }

    @Override
    public final <T, D extends Serializer<? extends T>> D findCustomSerializer(Class<? extends D> serializerClass)
            throws SerdeException {
        return registry.findCustomSerializer(serializerClass);
    }

    @Override
    public final <T> Serializer<? super T> findSerializer(Argument<? extends T> forType) throws SerdeException {
        return registry.findSerializer(forType);
    }

    @Override
    public <D extends PropertyNamingStrategy> D findNamingStrategy(Class<? extends D> namingStrategyClass) throws SerdeException {
        return registry.findNamingStrategy(namingStrategyClass);
    }

    @Override
    public <B, P> void pushManagedRef(PropertyReference<B, P> reference) {
        if (reference instanceof DocumentIdSerializationReference<?> documentIdReference) {
            Object bean = documentIdReference.getReference();
            if (bean != null) {
                IdentityHashMap<Object, Object> written = writtenBeans;
                if (written == null) {
                    written = new IdentityHashMap<>();
                    writtenBeans = written;
                }
                written.put(bean, documentIdReference.getReferenceName());
            }
        } else {
            super.pushManagedRef(reference);
        }
    }

    @Override
    public <B, P> @Nullable SerializationReference<B, P> resolveReference(SerializationReference<B, P> reference) {
        final Object value = reference.getReference();
        if (reference instanceof DocumentIdSerializationReference<?>) {
            IdentityHashMap<Object, Object> written = writtenBeans;
            return written != null && value != null && written.containsKey(value) ? null : reference;
        }
        if (refs != null) {
            final PropertyReference<?, ?> managedReference = refs.peekFirst();
            if (managedReference != null && managedReference.getProperty().getName().equals(reference.getReferenceName())) {
                if (managedReference.getReference() == value) {
                    return null;
                }
            }
        }
        return reference;
    }

    @Override
    public void close() {
        writtenBeans = null;
    }

    @Override
    public Optional<SerdeConfiguration> getSerdeConfiguration() {
        return Optional.of(registry.getSerdeConfiguration());
    }

    @Override
    public Optional<SerializationConfiguration> getSerializationConfiguration() {
        return Optional.of(registry.getSerializationConfiguration());
    }
}
