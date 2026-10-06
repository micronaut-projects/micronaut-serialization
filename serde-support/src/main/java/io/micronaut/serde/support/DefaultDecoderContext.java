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
import io.micronaut.serde.support.reference.DocumentIdReference;
import io.micronaut.serde.support.reference.PendingDocumentIdReference;
import io.micronaut.serde.util.SpecificSerdeTracker;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Default implementation of {@link io.micronaut.serde.Deserializer.DecoderContext}.
 *
 * @since 1.0.0
 */
@Internal
class DefaultDecoderContext extends AbstractPropertyReferenceManager implements Deserializer.DecoderContext, SpecificSerdeTracker {
    private final DefaultSerdeRegistry registry;
    // Document-scoped identifier state, allocated only when a document uses identifiers and released on close
    @Nullable
    private Map<String, Object> documentIds;
    @Nullable
    private Map<String, List<PendingDocumentIdReference>> pendingDocumentIds;

    /**
     * Whether this context is the context of one creation of a specific serde, which records whether the serde
     * depends on it instead of creating the serdes bound to a context.
     */
    private final boolean probing;
    private boolean bound;

    DefaultDecoderContext(DefaultSerdeRegistry registry) {
        this(registry, false);
    }

    private DefaultDecoderContext(DefaultSerdeRegistry registry, boolean probing) {
        this.registry = registry;
        this.probing = probing;
    }

    /**
     * Creates the context of one creation of a specific serde, which finds out whether the specific serde depends on
     * the context it is created with. A serde that the registry does not know to create its specific serdes
     * independently of the context is not created with it: it binds the creation to the context and a placeholder is
     * returned instead, so the specific serde created with this context is only kept when it is not bound.
     *
     * @param registry The registry
     * @return The context
     */
    static DefaultDecoderContext probing(DefaultSerdeRegistry registry) {
        return new DefaultDecoderContext(registry, true);
    }

    /**
     * @return Whether a specific serde created with this probing context is bound to the context
     */
    final boolean isBound() {
        return bound;
    }

    /**
     * Whether a specific serde created with this context can be reused with another context of the registry: the
     * context of a view or with other features creates other specific serdes.
     *
     * @param registry The registry
     * @return Whether the specific serdes are the ones of the registry
     */
    final boolean createsSpecificSerdesOf(DefaultSerdeRegistry registry) {
        return this.registry == registry && getClass() == DefaultDecoderContext.class;
    }

    @Override
    public final void markContextBound() {
        if (probing) {
            bound = true;
        }
    }

    @Override
    public final ConversionService getConversionService() {
        return registry.getConversionService();
    }

    @Override
    public final <T, D extends Deserializer<? extends T>> D findCustomDeserializer(Class<? extends D> deserializerClass)
            throws SerdeException {
        if (probing) {
            // A custom serde is written by the user
            bound = true;
            throw SpecificSerdeCache.BoundToContextException.INSTANCE;
        }
        return registry.findCustomDeserializer(deserializerClass);
    }

    @Override
    @SuppressWarnings("unchecked")
    public final <T> Deserializer<? extends T> findDeserializer(Argument<? extends T> type) throws SerdeException {
        Deserializer<? extends T> deserializer = registry.findDeserializer(type);
        if (probing && !registry.createsContextIndependentSerdes(deserializer)) {
            if (registry.isObjectDeserializer(deserializer)) {
                // The object deserializer is also found to create the specific deserializer of another type
                return (Deserializer<? extends T>) SpecificSerdeCache.probingObjectDeserializer((Deserializer<Object>) deserializer);
            }
            bound = true;
            return SpecificSerdeCache.boundDeserializer();
        }
        return deserializer;
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
    public <B, P> void pushManagedRef(PropertyReference<B, P> reference) {
        switch (reference) {
            case DocumentIdReference<?> documentIdReference -> registerDocumentId(documentIdReference);
            case PendingDocumentIdReference pendingReference -> {
                Map<String, List<PendingDocumentIdReference>> pending = pendingDocumentIds;
                if (pending == null) {
                    pending = new HashMap<>();
                    pendingDocumentIds = pending;
                }
                pending.computeIfAbsent(pendingReference.getReferenceName(), ignored -> new ArrayList<>(2)).add(pendingReference);
            }
            default -> super.pushManagedRef(reference);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <B, P> PropertyReference<B, P> resolveReference(PropertyReference<B, P> reference) {
        if (reference instanceof DocumentIdReference<?> lookup) {
            Map<String, Object> ids = documentIds;
            Object bean = ids == null ? null : ids.get(lookup.getReferenceName());
            return bean == null ? reference : (PropertyReference<B, P>) new DocumentIdReference<>(lookup.getReferenceName(), null, Argument.OBJECT_ARGUMENT, bean);
        }
        return resolveManagedRef(reference);
    }

    @SuppressWarnings("unchecked")
    private <B, P> PropertyReference<B, P> resolveManagedRef(PropertyReference<B, P> reference) {
        if (refs != null) {
            for (PropertyReference<?, ?> ref : refs) {
                if (ref.getReferenceName().equals(reference.getReferenceName())
                    || (reference.getProperty() != null
                    && ref.getReferenceName().equals(reference.getProperty().getName()))) {
                    final Object o = ref.getReference();
                    if (o != null) {
                        return (PropertyReference<B, P>) ref;
                    }
                }
            }
        }
        return reference;
    }

    private void registerDocumentId(DocumentIdReference<?> reference) {
        Object bean = reference.getReference();
        if (bean == null) {
            return;
        }
        Map<String, Object> ids = documentIds;
        if (ids == null) {
            ids = new HashMap<>();
            documentIds = ids;
        }
        String id = reference.getReferenceName();
        ids.put(id, bean);
        Map<String, List<PendingDocumentIdReference>> pending = pendingDocumentIds;
        List<PendingDocumentIdReference> pendingReferences = pending == null ? null : pending.remove(id);
        if (pendingReferences == null) {
            return;
        }
        try {
            for (PendingDocumentIdReference pendingReference : pendingReferences) {
                if (!pendingReference.isInstance(bean)) {
                    throw new SerdeException("Identifier [" + id + "] resolved to incompatible type ["
                        + bean.getClass().getName() + "] for [" + pendingReference.getTypeName() + "]");
                }
                pendingReference.getConsumer().accept(bean);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() throws IOException {
        Map<String, List<PendingDocumentIdReference>> pending = pendingDocumentIds;
        documentIds = null;
        pendingDocumentIds = null;
        if (pending != null && !pending.isEmpty()) {
            throw new SerdeException("Unresolved identifier references: " + pending.keySet());
        }
    }

    @Override
    public Optional<SerdeConfiguration> getSerdeConfiguration() {
        return Optional.of(registry.getSerdeConfiguration());
    }

    @Override
    public Optional<DeserializationConfiguration> getDeserializationConfiguration() {
        return Optional.of(registry.getDeserializationConfiguration());
    }
}
