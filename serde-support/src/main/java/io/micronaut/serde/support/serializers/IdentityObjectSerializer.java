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
package io.micronaut.serde.support.serializers;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.ObjectSerializer;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.reference.DocumentIdSerializationReference;
import io.micronaut.serde.support.util.DocumentIdUtil;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

/**
 * Serializes a bean whose document-scoped identifier carries object identity semantics ({@code @JsonIdentityInfo}):
 * the first occurrence in a document is written in full, every later occurrence is written as its identifier only.
 *
 * @param <T> The bean type
 * @since 3.2
 */
@Internal
final class IdentityObjectSerializer<T> implements ObjectSerializer<T> {
    private final Serializer<T> delegate;
    private final @Nullable ObjectSerializer<T> objectDelegate;
    private final BeanIntrospection<T> introspection;
    private final BeanProperty<T, Object> identityProperty;
    private final Argument<Object> identityArgument;
    private final Serializer<Object> identitySerializer;

    private IdentityObjectSerializer(Serializer<T> delegate,
                                     BeanIntrospection<T> introspection,
                                     BeanProperty<T, Object> identityProperty,
                                     Argument<Object> identityArgument,
                                     Serializer<Object> identitySerializer) {
        this.delegate = delegate;
        this.objectDelegate = delegate instanceof ObjectSerializer<T> objectSerializer ? objectSerializer : null;
        this.introspection = introspection;
        this.identityProperty = identityProperty;
        this.identityArgument = identityArgument;
        this.identitySerializer = identitySerializer;
    }

    @SuppressWarnings("unchecked")
    static <T> Serializer<T> create(Serializer<T> delegate,
                                    BeanIntrospection<T> introspection,
                                    BeanProperty<T, Object> identityProperty,
                                    EncoderContext context) throws SerdeException {
        Argument<Object> identityArgument = identityProperty.asArgument();
        Serializer<Object> identitySerializer = (Serializer<Object>) context.findSerializer(identityArgument)
            .createSpecific(context, identityArgument);
        return new IdentityObjectSerializer<>(delegate, introspection, identityProperty, identityArgument, identitySerializer);
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException {
        Object id = identityProperty.get(value);
        if (id != null) {
            DocumentIdSerializationReference<T> reference = new DocumentIdSerializationReference<>(id, introspection, identityArgument, value, identitySerializer);
            if (DocumentIdUtil.isWritten(context, reference)) {
                identitySerializer.serialize(encoder, context, identityArgument, id);
                return;
            }
            DocumentIdUtil.markWritten(context, reference);
        }
        delegate.serialize(encoder, context, type, value);
    }

    @Override
    public void serializeInto(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException {
        if (objectDelegate == null) {
            throw new SerdeException("Cannot serialize object identity type [" + type + "] into an existing object");
        }
        objectDelegate.serializeInto(encoder, context, type, value);
    }

    @Override
    public boolean isEmpty(EncoderContext context, @Nullable T value) {
        return delegate.isEmpty(context, value);
    }

    @Override
    public boolean isAbsent(EncoderContext context, @Nullable T value) {
        return delegate.isAbsent(context, value);
    }
}
