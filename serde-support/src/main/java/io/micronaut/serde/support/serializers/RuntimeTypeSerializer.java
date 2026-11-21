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
import io.micronaut.serde.util.DocumentSerdes;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * The runtime type serializer.
 */
@Internal
final class RuntimeTypeSerializer implements ObjectSerializer<Object> {
    private final EncoderContext encoderContext;
    @Nullable
    private final Serializer<Object> outer;
    @Nullable
    private final IntrospectionException introspectionException;
    private final Argument<?> outerType;

    public RuntimeTypeSerializer(EncoderContext encoderContext,
                                 @Nullable
                                 Serializer<Object> outer,
                                 @Nullable
                                 IntrospectionException introspectionException,
                                 Argument<?> outerType) {
        this.encoderContext = encoderContext;
        this.outer = outer;
        this.introspectionException = introspectionException;
        this.outerType = outerType;
    }

    public RuntimeTypeSerializer(EncoderContext encoderContext, Serializer<Object> outer, Argument<?> type) {
        this(encoderContext, outer, null, type);
    }

    public RuntimeTypeSerializer(EncoderContext encoderContext, IntrospectionException introspectionException, Argument<?> type) {
        this(encoderContext, null, introspectionException, type);
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<?> type, Object value)
        throws IOException {
        Class<?> t = value.getClass();
        Serializer<Object> serializer;
        if (outer != null && t == type.getType()) {
            serializer = outer;
        } else {
            type = Argument.of(t);
            serializer = getSerializer(context, value);
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
            type = Argument.of(t);
            serializer = getSerializer(context, value);
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

    /**
     * The serializers of the runtime types of one document. A serializer may be bound to the context it was created
     * with (a serde bean taking the context as a constructor argument), and this serializer can outlive a document,
     * as the property serializer of a bean, so the serializers are kept by the document context and released with
     * it. A context that keeps no document serdes gets new serializers for each value.
     */
    @SuppressWarnings("unchecked")
    private Map<Class<?>, Serializer<Object>> documentSerializers(EncoderContext context) {
        if (DocumentSerdes.get(context, this) instanceof Map<?, ?> serializers) {
            return (Map<Class<?>, Serializer<Object>>) serializers;
        }
        Map<Class<?>, Serializer<Object>> serializers = new HashMap<>(4);
        DocumentSerdes.put(context, this, serializers);
        return serializers;
    }

    private Serializer<Object> getSerializer(EncoderContext context, Object value) throws SerdeException {
        Map<Class<?>, Serializer<Object>> serializers = documentSerializers(context);
        Class<?> type = value.getClass();
        Serializer<Object> serializer = serializers.get(type);
        if (serializer == null) {
            // not computeIfAbsent: creating a serializer may look up the serializer of another runtime type
            serializer = createSerializer(context, type);
            serializers.put(type, serializer);
        }
        return serializer;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Serializer<Object> createSerializer(EncoderContext context, Class<?> type) throws SerdeException {
        if (type.equals(outerType.getType())) {
            if (outer != null) {
                return outer;
            }
            if (introspectionException != null) {
                String message = introspectionException.getMessage();
                throw new SerdeException(message == null ? "No serializer found for type: " + type : message, introspectionException);
            }
            throw new SerdeException("No serializer found for type: " + type);
        }
        Argument<Object> arg = Argument.of((Class) type, outerType.getAnnotationMetadata());
        return encoderContext.findSerializer(arg).createSpecific(context, arg);
    }

}
