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
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.reference.PropertyReference;
import org.jspecify.annotations.Nullable;

/**
 * A bean registered under, or looked up by, its document-scoped identifier. Pushed through
 * {@link io.micronaut.serde.reference.PropertyReferenceManager#pushManagedRef} to register the bean for the
 * current document and passed to {@link io.micronaut.serde.Deserializer.DecoderContext#resolveReference} to look
 * it up; the reference name is the identifier.
 *
 * @param <B> The bean type
 * @since 3.2
 */
@Internal
public final class DocumentIdReference<B> extends PropertyReference<B, Object> {

    /**
     * Creates a document identifier reference.
     *
     * @param id The identifier
     * @param introspection The bean introspection, {@code null} for a lookup
     * @param idArgument The identifier property
     * @param bean The bean, {@code null} for a lookup
     */
    public DocumentIdReference(String id, @Nullable BeanIntrospection<B> introspection, Argument<Object> idArgument, @Nullable B bean) {
        super(id, introspection, idArgument, bean);
    }
}
