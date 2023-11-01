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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.ObjectSerializer;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The runtime type serializer.
 */
@Internal
final class RuntimeTypeSerializer implements ObjectSerializer<Object> {
    private final EncoderContext encoderContext;
    private final Map<Class<?>, Serializer<Object>> inners = new ConcurrentHashMap<>(10);
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
        if (value == null) {
            encoder.encodeNull();
        } else {
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
    public boolean isEmpty(EncoderContext context, Object value) {
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
    public boolean isAbsent(EncoderContext context, Object value) {
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
        try {
            return inners.computeIfAbsent(value.getClass(), t -> {
                try {
                    if (value.getClass().equals(outerType.getType())) {
                        if (outer == null) {
                            throw new SerdeException(introspectionException.getMessage(), introspectionException);
                        }
                        return outer;
                    } else {
                        Argument<Object> arg = Argument.of((Class) value.getClass());
                        return encoderContext.findSerializer(arg).createSpecific(context, arg);
                    }
                } catch (SerdeException ex) {
                    throw new IntrospectionException("No serializer found for type: " + value.getClass(), ex);
                }
            });
        } catch (IntrospectionException e) {
            if (e.getCause() instanceof SerdeException serdeException) {
                throw serdeException;
            } else {
                throw e;
            }
        }
    }

}
