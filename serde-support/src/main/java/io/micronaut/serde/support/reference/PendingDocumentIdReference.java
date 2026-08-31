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
package io.micronaut.serde.support.reference;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.reference.PropertyReference;

import java.io.IOException;

/**
 * A reference to a bean that has not been read yet. Pushed through
 * {@link io.micronaut.serde.reference.PropertyReferenceManager#pushManagedRef}; the consumer receives the bean once
 * a {@link DocumentIdReference} with the same identifier is registered, and the document fails to complete if that
 * never happens.
 *
 * @since 3.2
 */
@Internal
public final class PendingDocumentIdReference extends PropertyReference<Object, Object> {
    private final Argument<?> type;
    private final DocumentIdConsumer consumer;

    /**
     * Creates a pending reference.
     *
     * @param id The identifier
     * @param type The expected bean type
     * @param consumer Receives the bean
     */
    public PendingDocumentIdReference(String id, Argument<?> type, DocumentIdConsumer consumer) {
        super(id, null, Argument.OBJECT_ARGUMENT, null);
        this.type = type;
        this.consumer = consumer;
    }

    /**
     * Checks whether a bean is compatible with the expected bean type.
     *
     * @param bean The bean
     * @return Whether the bean is an instance of the expected bean type
     */
    public boolean isInstance(Object bean) {
        return type.getType().isInstance(bean);
    }

    /**
     * Returns the name of the expected bean type.
     *
     * @return The name of the expected bean type
     */
    public String getTypeName() {
        return type.getType().getName();
    }

    /**
     * Returns the consumer of the bean.
     *
     * @return The consumer of the bean
     */
    public DocumentIdConsumer getConsumer() {
        return consumer;
    }

    /**
     * Receives the bean once its identifier is registered.
     */
    @FunctionalInterface
    public interface DocumentIdConsumer {
        /**
         * Receives the resolved bean.
         *
         * @param bean The bean
         * @throws IOException If the reference cannot be set
         */
        void accept(Object bean) throws IOException;
    }
}
