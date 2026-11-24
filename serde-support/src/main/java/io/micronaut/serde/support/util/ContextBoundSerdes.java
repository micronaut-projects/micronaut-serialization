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
import io.micronaut.serde.UpdatingDeserializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.util.DocumentSerdes;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks the serde beans created with the context of a document, such as a user serde taking
 * {@code @Parameter EncoderContext}, while a serde is specialized, so that the serdes of a bean that are kept
 * between documents are specialized again for each document when they contain one.
 *
 * @since 3.3.0
 */
@Internal
public final class ContextBoundSerdes {

    /**
     * Per thread: the number of serde beans created with a context while a specialization is tracked, and the depth
     * of the nested specializations. The pair is set by the outermost specialization and removed when it completes.
     */
    private static final ThreadLocal<int[]> TRACKING = new ThreadLocal<>();
    private static final int CREATED = 0;
    private static final int DEPTH = 1;

    private ContextBoundSerdes() {
    }

    /**
     * Records that a serde bean was created with the context of the current specialization.
     */
    public static void created() {
        int[] tracking = TRACKING.get();
        if (tracking != null && tracking[DEPTH] > 0) {
            tracking[CREATED]++;
        }
    }

    /**
     * Specializes a serializer that a bean keeps between documents. If specializing it creates a serde bean bound to
     * the context, the serializer is specialized again for the context of each document.
     *
     * @param factory The factory of the specific serializer
     * @param context The context of the current document
     * @param <T>     The type
     * @return The serializer to keep
     * @throws SerdeException If it cannot be created
     */
    public static <T> Serializer<T> specialize(SerializerFactory<T> factory, Serializer.EncoderContext context) throws SerdeException {
        int[] tracking = enter();
        int created = tracking[CREATED];
        try {
            Serializer<T> specific = factory.create(context);
            return wrapsPerDocument(tracking, created, context) ? perDocument(factory, context, specific) : specific;
        } finally {
            exit(tracking);
        }
    }

    /**
     * Specializes a deserializer that a bean keeps between documents. If specializing it creates a serde bean bound to
     * the context, the deserializer is specialized again for the context of each document.
     *
     * @param factory The factory of the specific deserializer
     * @param context The context of the current document
     * @param <T>     The type
     * @return The deserializer to keep
     * @throws SerdeException If it cannot be created
     */
    public static <T> Deserializer<T> specialize(DeserializerFactory<T> factory, Deserializer.DecoderContext context) throws SerdeException {
        int[] tracking = enter();
        int created = tracking[CREATED];
        try {
            Deserializer<T> specific = factory.create(context);
            return wrapsPerDocument(tracking, created, context) ? perDocument(factory, context, specific) : specific;
        } finally {
            exit(tracking);
        }
    }

    private static int[] enter() {
        int[] tracking = TRACKING.get();
        if (tracking == null) {
            tracking = new int[2];
            TRACKING.set(tracking);
        }
        tracking[DEPTH]++;
        return tracking;
    }

    private static void exit(int[] tracking) {
        if (--tracking[DEPTH] == 0) {
            TRACKING.remove();
        }
    }

    /**
     * Whether the serde specialized since {@code created} is specialized again for each document. Only a context that
     * keeps document serdes can hold the specializations: with any other context the serde stays shared, as before.
     * The per-document serde isolates the context, so the serde beans it created no longer count for the enclosing
     * specializations, which keep their shared serde.
     */
    private static boolean wrapsPerDocument(int[] tracking, int created, Object context) {
        if (tracking[CREATED] == created || !DocumentSerdes.keeps(context)) {
            return false;
        }
        tracking[CREATED] = created;
        return true;
    }

    private static <T> Serializer<T> perDocument(SerializerFactory<T> factory, Serializer.EncoderContext context, Serializer<T> first) {
        // A serializer that cannot serialize into an object keeps that capability hidden, so callers keep their fallback
        PerDocumentSerializer<T> serializer = first instanceof ObjectSerializer<T>
            ? new PerDocumentObjectSerializer<>(factory)
            : new PerDocumentSerializer<>(factory);
        serializer.store(context, first);
        return serializer;
    }

    private static <T> Deserializer<T> perDocument(DeserializerFactory<T> factory, Deserializer.DecoderContext context, Deserializer<T> first) {
        // A deserializer that updates existing values keeps doing so, so that merging is not turned into replacing
        PerDocumentDeserializer<T> deserializer = first instanceof UpdatingDeserializer<T>
            ? new PerDocumentUpdatingDeserializer<>(factory)
            : new PerDocumentDeserializer<>(factory);
        deserializer.store(context, first);
        return deserializer;
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
     * A serializer specialized again for the context of each document. The specializations are kept by the document
     * context, not by this serializer, since a specialization can keep its context.
     *
     * @param <T> The type
     */
    private static class PerDocumentSerializer<T> implements Serializer<T> {
        private final SerializerFactory<T> factory;
        private final AtomicReference<Serializer<T>> shared = new AtomicReference<>();

        PerDocumentSerializer(SerializerFactory<T> factory) {
            this.factory = factory;
        }

        final void store(Serializer.EncoderContext context, Serializer<T> specific) {
            DocumentSerdes.put(context, this, specific);
        }

        /**
         * A context that keeps no document serdes, such as a context of another module delegating to a document
         * context, gets one specialization shared by all such contexts, as every context did before.
         */
        private Serializer<T> shared(Serializer.EncoderContext context) throws SerdeException {
            Serializer<T> existing = shared.get();
            if (existing != null) {
                return existing;
            }
            Serializer<T> created = factory.create(context);
            // Another thread may have created it first: keep the one stored
            Serializer<T> witness = shared.compareAndExchange(null, created);
            return witness != null ? witness : created;
        }

        @SuppressWarnings("unchecked")
        final Serializer<T> specific(Serializer.EncoderContext context) throws SerdeException {
            if (DocumentSerdes.get(context, this) instanceof Serializer<?> specific) {
                return (Serializer<T>) specific;
            }
            if (!DocumentSerdes.keeps(context)) {
                return shared(context);
            }
            Serializer<T> specific = factory.create(context);
            store(context, specific);
            return specific;
        }

        @Override
        public final void serialize(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException {
            specific(context).serialize(encoder, context, type, value);
        }

        @Override
        public final boolean isEmpty(EncoderContext context, @Nullable T value) {
            try {
                return specific(context).isEmpty(context, value);
            } catch (SerdeException _) {
                return Serializer.super.isEmpty(context, value);
            }
        }

        @Override
        public final boolean isAbsent(EncoderContext context, @Nullable T value) {
            try {
                return specific(context).isAbsent(context, value);
            } catch (SerdeException _) {
                return Serializer.super.isAbsent(context, value);
            }
        }

        @Override
        public final boolean isDefault(EncoderContext context, T value) {
            try {
                return specific(context).isDefault(context, value);
            } catch (SerdeException _) {
                return Serializer.super.isDefault(context, value);
            }
        }
    }

    /**
     * A per-document serializer of a type that can be serialized into an existing object.
     *
     * @param <T> The type
     */
    private static final class PerDocumentObjectSerializer<T> extends PerDocumentSerializer<T> implements ObjectSerializer<T> {
        PerDocumentObjectSerializer(SerializerFactory<T> factory) {
            super(factory);
        }

        @Override
        public void serializeInto(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException {
            if (specific(context) instanceof ObjectSerializer<T> objectSerializer) {
                objectSerializer.serializeInto(encoder, context, type, value);
            } else {
                throw new SerdeException("Serializer for type: " + type + " doesn't support serializing into an existing object");
            }
        }
    }

    /**
     * A deserializer specialized again for the context of each document. The specializations are kept by the
     * document context, not by this deserializer, since a specialization can keep its context.
     *
     * @param <T> The type
     */
    private static class PerDocumentDeserializer<T> implements Deserializer<T> {
        private final DeserializerFactory<T> factory;
        private final AtomicReference<Deserializer<T>> shared = new AtomicReference<>();

        PerDocumentDeserializer(DeserializerFactory<T> factory) {
            this.factory = factory;
        }

        final void store(Deserializer.DecoderContext context, Deserializer<T> specific) {
            DocumentSerdes.put(context, this, specific);
        }

        /**
         * A context that keeps no document serdes, such as a context of another module delegating to a document
         * context, gets one specialization shared by all such contexts, as every context did before.
         */
        private Deserializer<T> shared(Deserializer.DecoderContext context) throws SerdeException {
            Deserializer<T> existing = shared.get();
            if (existing != null) {
                return existing;
            }
            Deserializer<T> created = factory.create(context);
            // Another thread may have created it first: keep the one stored
            Deserializer<T> witness = shared.compareAndExchange(null, created);
            return witness != null ? witness : created;
        }

        @SuppressWarnings("unchecked")
        final Deserializer<T> specific(Deserializer.DecoderContext context) throws SerdeException {
            if (DocumentSerdes.get(context, this) instanceof Deserializer<?> specific) {
                return (Deserializer<T>) specific;
            }
            if (!DocumentSerdes.keeps(context)) {
                return shared(context);
            }
            Deserializer<T> specific = factory.create(context);
            store(context, specific);
            return specific;
        }

        @Override
        public final T deserialize(Decoder decoder, DecoderContext context, Argument<? super T> type) throws IOException {
            return specific(context).deserialize(decoder, context, type);
        }

        @Override
        public final @Nullable T deserializeNullable(Decoder decoder, DecoderContext context, Argument<? super T> type) throws IOException {
            return specific(context).deserializeNullable(decoder, context, type);
        }

        @Override
        public final @Nullable T getDefaultValue(DecoderContext context, Argument<? super T> type) {
            Deserializer<T> specific;
            try {
                specific = specific(context);
            } catch (SerdeException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            return specific.getDefaultValue(context, type);
        }
    }

    /**
     * A per-document deserializer of a type whose values can be updated in place, such as with {@code @JsonMerge}.
     *
     * @param <T> The type
     */
    private static final class PerDocumentUpdatingDeserializer<T> extends PerDocumentDeserializer<T> implements UpdatingDeserializer<T> {
        PerDocumentUpdatingDeserializer(DeserializerFactory<T> factory) {
            super(factory);
        }

        @Override
        public void deserializeInto(Decoder decoder, DecoderContext context, Argument<? super T> type, T value) throws IOException {
            if (specific(context) instanceof UpdatingDeserializer<T> updatingDeserializer) {
                updatingDeserializer.deserializeInto(decoder, context, type, value);
            } else {
                throw new SerdeException("Deserializer for type: " + type + " doesn't support updating an existing value");
            }
        }
    }
}
