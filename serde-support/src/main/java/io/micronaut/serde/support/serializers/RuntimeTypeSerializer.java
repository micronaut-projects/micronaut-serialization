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
import io.micronaut.serde.support.util.ContextBoundSerdes;
import io.micronaut.serde.util.DocumentContextTracker;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The runtime type serializer.
 */
@Internal
final class RuntimeTypeSerializer implements ObjectSerializer<Object> {
    private static final String NO_SERIALIZER_FOUND = "No serializer found for type: ";

    /**
     * The serializers of the runtime types. A serializer whose creation bound it to the context of the document, such as
     * one holding a serde bean that takes the context as a constructor argument, or one created by a user serde, is
     * specialized again for each document by {@link ContextBoundSerdes}, which keeps the specializations in the document
     * context. Each is kept with the argument it is called with, so that a value does not build an argument.
     */
    private final Map<Class<?>, Inner> inners = new ConcurrentHashMap<>(4);
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
            Inner inner = getInner(context, value);
            type = inner.argument();
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
            Inner inner = getInner(context, value);
            type = inner.argument();
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
            return getSerializer(context, value).isEmpty(context, value);
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
            return getSerializer(context, value).isAbsent(context, value);
        } catch (SerdeException e) {
            // will fail later
        }
        return ObjectSerializer.super.isAbsent(context, value);
    }

    private Serializer<Object> getSerializer(EncoderContext context, Object value) throws SerdeException {
        return getInner(context, value).serializer();
    }

    private Inner getInner(EncoderContext context, Object value) throws SerdeException {
        Class<?> type = value.getClass();
        Inner inner = inners.get(type);
        if (inner == null) {
            // not computeIfAbsent: creating a serializer may look up the serializer of another runtime type
            inner = new Inner(Argument.of(type), ContextBoundSerdes.specialize(c -> createSerializer(c, type), context));
            Inner existing = inners.putIfAbsent(type, inner);
            if (existing != null) {
                inner = existing;
            }
        }
        return inner;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Serializer<Object> createSerializer(EncoderContext context, Class<?> type) throws SerdeException {
        if (type.equals(outerType.getType())) {
            if (outer != null) {
                return outer;
            }
            if (introspectionException != null) {
                String message = introspectionException.getMessage();
                throw new SerdeException(message == null ? NO_SERIALIZER_FOUND + type : message, introspectionException);
            }
            throw new SerdeException(NO_SERIALIZER_FOUND + type);
        }
        Argument<Object> arg = Argument.of((Class) type, outerType.getAnnotationMetadata());
        int bindings = DocumentContextTracker.documentBindings(context);
        // the context of the call, not of the creation: this serializer can serve several documents
        Serializer<Object> serializer = context.findSerializer(arg).createSpecific(context, arg);
        if (bindings >= 0 && DocumentContextTracker.documentBindings(context) != bindings) {
            // a user serde was looked up: the serializer it created can keep the context of this document
            ContextBoundSerdes.created();
        }
        return serializer;
    }

    /**
     * The serializer of a runtime type and the argument it is called with.
     *
     * @param argument   The argument of the runtime type
     * @param serializer The serializer
     */
    private record Inner(Argument<?> argument, Serializer<Object> serializer) {
    }

}
