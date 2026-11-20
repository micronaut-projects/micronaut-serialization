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
package io.micronaut.serde.support.util;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.ObjectSerializer;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Tracks the serde beans created with the context of a document, such as a user serde taking
 * {@code @Parameter EncoderContext}, while a serde is specialized, so that the serdes of a bean that are kept
 * between documents are specialized again for each document when they contain one.
 *
 * @since 3.3.0
 */
@Internal
public final class ContextBoundSerdes {

    private static final ThreadLocal<int[]> CREATED = ThreadLocal.withInitial(() -> new int[1]);

    private ContextBoundSerdes() {
    }

    /**
     * Records that a serde bean was created with the context of the current specialization.
     */
    public static void created() {
        CREATED.get()[0]++;
    }

    /**
     * @return The number of serde beans created with a context on this thread, to compare before and after a
     * specialization
     */
    public static int count() {
        return CREATED.get()[0];
    }

    /**
     * Creates a specific serializer for each context.
     *
     * @param <T> The type
     */
    @FunctionalInterface
    public interface SerializerFactory<T> {
        /**
         * @param context The context of the document
         * @return The specific serializer
         * @throws SerdeException If it cannot be created
         */
        Serializer<T> create(Serializer.EncoderContext context) throws SerdeException;
    }

    /**
     * Creates a specific deserializer for each context.
     *
     * @param <T> The type
     */
    @FunctionalInterface
    public interface DeserializerFactory<T> {
        /**
         * @param context The context of the document
         * @return The specific deserializer
         * @throws SerdeException If it cannot be created
         */
        Deserializer<T> create(Deserializer.DecoderContext context) throws SerdeException;
    }

    /**
     * A serializer specialized again for each context it serves, starting from the one specialized with the first.
     *
     * @param <T> The type
     */
    public static final class PerContextSerializer<T> implements ObjectSerializer<T> {
        private final SerializerFactory<T> factory;
        private final Map<Serializer.EncoderContext, Serializer<T>> specifics = Collections.synchronizedMap(new WeakHashMap<>());

        /**
         * @param factory The factory of the specific serializer
         * @param context The context the first specific serializer was created with
         * @param first   The first specific serializer
         */
        public PerContextSerializer(SerializerFactory<T> factory, Serializer.EncoderContext context, Serializer<T> first) {
            this.factory = factory;
            specifics.put(context, first);
        }

        private Serializer<T> specific(Serializer.EncoderContext context) throws SerdeException {
            Serializer<T> specific = specifics.get(context);
            if (specific == null) {
                specific = factory.create(context);
                specifics.put(context, specific);
            }
            return specific;
        }

        @Override
        public void serialize(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException {
            specific(context).serialize(encoder, context, type, value);
        }

        @Override
        public void serializeInto(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException {
            if (specific(context) instanceof ObjectSerializer<T> objectSerializer) {
                objectSerializer.serializeInto(encoder, context, type, value);
            } else {
                throw new SerdeException("Serializer for type: " + type + " doesn't support serializing into an existing object");
            }
        }

        @Override
        public boolean isEmpty(EncoderContext context, @Nullable T value) {
            try {
                return specific(context).isEmpty(context, value);
            } catch (SerdeException e) {
                return ObjectSerializer.super.isEmpty(context, value);
            }
        }

        @Override
        public boolean isAbsent(EncoderContext context, @Nullable T value) {
            try {
                return specific(context).isAbsent(context, value);
            } catch (SerdeException e) {
                return ObjectSerializer.super.isAbsent(context, value);
            }
        }
    }

    /**
     * A deserializer specialized again for each context it serves, starting from the one specialized with the first.
     *
     * @param <T> The type
     */
    public static final class PerContextDeserializer<T> implements Deserializer<T> {
        private final DeserializerFactory<T> factory;
        private final Deserializer<T> first;
        private final Map<Deserializer.DecoderContext, Deserializer<T>> specifics = Collections.synchronizedMap(new WeakHashMap<>());

        /**
         * @param factory The factory of the specific deserializer
         * @param context The context the first specific deserializer was created with
         * @param first   The first specific deserializer
         */
        public PerContextDeserializer(DeserializerFactory<T> factory, Deserializer.DecoderContext context, Deserializer<T> first) {
            this.factory = factory;
            this.first = first;
            specifics.put(context, first);
        }

        private Deserializer<T> specific(Deserializer.DecoderContext context) throws SerdeException {
            Deserializer<T> specific = specifics.get(context);
            if (specific == null) {
                specific = factory.create(context);
                specifics.put(context, specific);
            }
            return specific;
        }

        @Override
        public T deserialize(Decoder decoder, DecoderContext context, Argument<? super T> type) throws IOException {
            return specific(context).deserialize(decoder, context, type);
        }

        @Override
        public @Nullable T deserializeNullable(Decoder decoder, DecoderContext context, Argument<? super T> type) throws IOException {
            return specific(context).deserializeNullable(decoder, context, type);
        }

        @Override
        public @Nullable T getDefaultValue(DecoderContext context, Argument<? super T> type) {
            return first.getDefaultValue(context, type);
        }
    }
}
