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
import io.micronaut.serde.Serializer;
import io.micronaut.serde.reference.SerializationReference;

/**
 * A bean written under its document-scoped identifier. Pushed through
 * {@link io.micronaut.serde.reference.PropertyReferenceManager#pushManagedRef} once the bean has been written in
 * full; {@link io.micronaut.serde.Serializer.EncoderContext#resolveReference} then returns {@code null} for the
 * same bean, meaning it must be written as its identifier only.
 *
 * @param <B> The bean type
 * @since 3.2
 */
@Internal
public final class DocumentIdSerializationReference<B> extends SerializationReference<B, Object> {

    /**
     * Creates a document identifier serialization reference.
     *
     * @param id The identifier
     * @param introspection The bean introspection
     * @param idArgument The identifier property
     * @param bean The bean
     * @param idSerializer The identifier serializer
     */
    public DocumentIdSerializationReference(Object id,
                                            BeanIntrospection<B> introspection,
                                            Argument<Object> idArgument,
                                            B bean,
                                            Serializer<Object> idSerializer) {
        super(String.valueOf(id), introspection, idArgument, bean, idSerializer);
    }
}
