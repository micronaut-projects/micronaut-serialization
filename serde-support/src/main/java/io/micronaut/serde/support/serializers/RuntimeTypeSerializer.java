/*
 * Copyright 2017-2023 original authors
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
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.ObjectSerializer;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.util.DocumentContextTracker;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The runtime type serializer.
 */
@Internal
final class RuntimeTypeSerializer implements ObjectSerializer<Object> {
    private final Map<Class<?>, Inner> inners = new ConcurrentHashMap<>(10);
    @Nullable
    private final Serializer<Object> outer;
    @Nullable
    private final IntrospectionException introspectionException;
    private final Argument<?> outerType;

    public RuntimeTypeSerializer(@Nullable
                                 Serializer<Object> outer,
                                 @Nullable
                                 IntrospectionException introspectionException,
                                 Argument<?> outerType) {
        this.outer = outer;
        this.introspectionException = introspectionException;
        this.outerType = outerType;
    }

    public RuntimeTypeSerializer(Serializer<Object> outer, Argument<?> type) {
        this(outer, null, type);
    }

    public RuntimeTypeSerializer(IntrospectionException introspectionException, Argument<?> type) {
        this(null, introspectionException, type);
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<?> type, Object value)
        throws IOException {
        Class<?> t = value.getClass();
        Serializer<Object> serializer;
        if (outer != null && t == type.getType()) {
            serializer = outer;
        } else {
            Inner inner = getInner(context, t);
            type = inner.type();
            serializer = inner.serializer();
        }
        serializer.serialize(encoder, context, type, value);
    }

    @Override
    public void serializeInto(Encoder encoder, EncoderContext context, Argument<?> type, Object value) throws IOException {
        Class<?> t = value.getClass();
        Serializer<Object> serializer;
        if (outer != null && t == type.getType()) {
            serializer = outer;
        } else {
            Inner inner = getInner(context, t);
            type = inner.type();
            serializer = inner.serializer();
        }
        if (serializer instanceof ObjectSerializer<Object> objectSerializer) {
            objectSerializer.serializeInto(encoder, context, type, value);
        } else {
            throw serializeIntoNotSupported(type);
        }
    }

    private SerdeException serializeIntoNotSupported(Argument<?> type) {
        return new SerdeException("Serializer for type: " + type + " doesn't support serializing into an existing object");
    }

    @Override
    public boolean isEmpty(EncoderContext context, @Nullable Object value) {
        if (value == null) {
            return true;
        }
        try {
            return getInner(context, value.getClass()).serializer().isEmpty(context, value);
        } catch (SerdeException e) {
            // will fail later
        }
        return ObjectSerializer.super.isEmpty(context, value);
    }

    @Override
    public boolean isAbsent(EncoderContext context, @Nullable Object value) {
        if (value == null) {
            return true;
        }
        try {
            return getInner(context, value.getClass()).serializer().isAbsent(context, value);
        } catch (SerdeException e) {
            // will fail later
        }
        return ObjectSerializer.super.isAbsent(context, value);
    }

    private Inner getInner(EncoderContext context, Class<?> type) throws SerdeException {
        Inner inner = inners.get(type);
        if (inner != null && (inner.document() == null || inner.document() == context)) {
            return inner;
        }
        Map<Class<?>, Inner> documentInners = (Map<Class<?>, Inner>) DocumentContextTracker.getDocumentValue(context, this);
        if (documentInners != null) {
            inner = documentInners.get(type);
            if (inner != null) {
                return inner;
            }
        }
        int bindings = DocumentContextTracker.documentBindings(context);
        Serializer<Object> serializer = createSerializer(context, type);
        Argument<Object> argument = Argument.of((Class<Object>) type);
        // This serializer can serve several documents (a mapper caches the serializers that are not bound to a
        // document), so a serializer that the creation bound to the document is kept by the document, which
        // releases it with the values it holds when the document is finished.
        if (bindings >= 0 && DocumentContextTracker.documentBindings(context) == bindings) {
            inner = new Inner(argument, serializer, null);
            inners.put(type, inner);
            return inner;
        }
        inner = new Inner(argument, serializer, context);
        if (documentInners == null) {
            documentInners = new HashMap<>(4);
            if (!DocumentContextTracker.putDocumentValue(context, this, documentInners)) {
                // a context that does not keep values: the serializer is reused only with this context
                inners.put(type, inner);
                return inner;
            }
        }
        documentInners.put(type, inner);
        return inner;
    }

    private Serializer<Object> createSerializer(EncoderContext context, Class<?> type) throws SerdeException {
        if (type.equals(outerType.getType())) {
            if (outer == null) {
                if (introspectionException != null) {
                    String message = introspectionException.getMessage();
                    if (message == null) {
                        message = "No serializer found for type: " + type;
                    }
                    throw new SerdeException(message, introspectionException);
                }
                throw new SerdeException("No serializer found for type: " + type);
            }
            return outer;
        }
        Argument<Object> arg = Argument.of((Class<Object>) type, outerType.getAnnotationMetadata());
        return context.findSerializer(arg).createSpecific(context, arg);
    }

    /**
     * The serializer of a runtime type.
     *
     * @param type       The runtime type
     * @param serializer The serializer
     * @param document   The context the serializer is bound to, null if it is not bound
     */
    private record Inner(Argument<Object> type, Serializer<Object> serializer, @Nullable Object document) {
    }
}
