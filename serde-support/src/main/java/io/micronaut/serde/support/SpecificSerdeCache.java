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
package io.micronaut.serde.support;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.util.SpecificSerdeTracker;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The specific serdes of a generated serde that the registry reuses for later documents, by argument.
 *
 * <p>A generated serde is created with a context and the type, and resolves the specific serdes of its properties
 * with that context. To find out whether the specific serde depends on the context of the document, the registry
 * first creates it with a context of its own for that creation only (a probing context, see
 * {@link DefaultDecoderContext#probing(DefaultSerdeRegistry)}). The probing context records whether anything
 * created with it is bound to it: a serde the registry does not know to create its specific serdes independently of
 * the context, such as a serde written by the user, is not even created with it, and a serde that keeps the context
 * to resolve other serdes later marks it ({@link io.micronaut.serde.util.SpecificSerdeTracker}). A specific serde
 * created with the probing context that is not bound to it does not depend on any context and is kept. Otherwise the
 * argument is recorded as bound, and its specific serde is created with the context of every document, as without
 * this cache.</p>
 *
 * <p>An argument is the same argument when it has the same type, name, type parameters and annotation metadata
 * instance, since the specific serde can depend on all of them.</p>
 *
 * @param <S> The serde type
 */
@Internal
final class SpecificSerdeCache<S> {

    /**
     * Limits the arguments of a serde whose specific serde or verdict is kept, such as arguments created with new
     * annotation metadata for every document: those are created for every document as before.
     */
    static final int MAX_ARGUMENTS = 128;

    /**
     * The verdict of an argument whose specific serde is bound to the context of the document.
     */
    private static final Object BOUND = new Object();

    private final Map<Key, Object> serdes = new ConcurrentHashMap<>();
    /**
     * The last argument found, so that the same argument instance is found without allocating a key.
     */
    private final AtomicReference<@Nullable Entry> last = new AtomicReference<>();

    /**
     * Find the specific serde of the argument.
     *
     * @param type The argument
     * @return The specific serde, {@link #isBound(Object)} if it is created for every document, or null if it is
     * not known yet
     */
    @Nullable
    Object get(Argument<?> type) {
        Entry entry = last.get();
        if (entry != null && entry.type == type) {
            return entry.value;
        }
        Object value = serdes.get(new Key(type));
        if (value != null) {
            last.set(new Entry(type, value));
        }
        return value;
    }

    /**
     * @param value A value returned by {@link #get(Argument)}
     * @return Whether the specific serde of the argument is bound to the context of the document
     */
    static boolean isBound(@Nullable Object value) {
        return value == BOUND;
    }

    /**
     * Keep the specific serde of the argument.
     *
     * @param type  The argument
     * @param serde The specific serde
     */
    void put(Argument<?> type, S serde) {
        put0(type, serde);
    }

    /**
     * Record that the specific serde of the argument is bound to the context of the document.
     *
     * @param type The argument
     */
    void putBound(Argument<?> type) {
        put0(type, BOUND);
    }

    private void put0(Argument<?> type, Object value) {
        if (serdes.size() < MAX_ARGUMENTS) {
            serdes.putIfAbsent(new Key(type), value);
            last.set(new Entry(type, value));
        }
    }

    /**
     * @return The number of arguments whose specific serde or verdict is kept
     */
    int size() {
        return serdes.size();
    }

    static boolean sameArgument(Argument<?> a, Argument<?> b) {
        if (a == b) {
            return true;
        }
        if (a.getClass() != b.getClass() || a.getType() != b.getType() || !a.getName().equals(b.getName())) {
            return false;
        }
        AnnotationMetadata metadata = a.getAnnotationMetadata();
        AnnotationMetadata otherMetadata = b.getAnnotationMetadata();
        if (metadata != otherMetadata && !(metadata.isEmpty() && otherMetadata.isEmpty())) {
            return false;
        }
        Argument<?>[] parameters = a.getTypeParameters();
        Argument<?>[] otherParameters = b.getTypeParameters();
        if (parameters.length != otherParameters.length) {
            return false;
        }
        for (int i = 0; i < parameters.length; i++) {
            if (!sameArgument(parameters[i], otherParameters[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param <T> The type
     * @return The deserializer a probing context returns in place of a deserializer bound to the context
     */
    @SuppressWarnings("unchecked")
    static <T> Deserializer<T> boundDeserializer() {
        return (Deserializer<T>) BoundDeserializer.INSTANCE;
    }

    /**
     * @param <T> The type
     * @return The serializer a probing context returns in place of a serializer bound to the context
     */
    @SuppressWarnings("unchecked")
    static <T> Serializer<T> boundSerializer() {
        return (Serializer<T>) BoundSerializer.INSTANCE;
    }

    /**
     * The runtime object deserializer as a probing context finds it: it creates the deserializer of arbitrary values
     * for {@code Object}, which does not depend on the context. For any other type it keeps the property deserializers
     * in descriptions shared by every document, so the creation is bound to the context instead.
     *
     * @param objectDeserializer The runtime object deserializer
     * @return The deserializer
     */
    static Deserializer<Object> probingObjectDeserializer(Deserializer<Object> objectDeserializer) {
        return new ProbingObjectDeserializer(objectDeserializer);
    }

    /**
     * Thrown by a probing context in place of a custom serde, which is bound to the context: the creation is
     * abandoned and the specific serde is created with the context of the document.
     */
    static final class BoundToContextException extends RuntimeException {
        static final BoundToContextException INSTANCE = new BoundToContextException();
        private static final long serialVersionUID = 1L;

        private BoundToContextException() {
            super("The specific serde is bound to the context", null, false, false);
        }
    }

    /**
     * See {@link #probingObjectDeserializer(Deserializer)}.
     *
     * @param objectDeserializer The runtime object deserializer
     */
    private record ProbingObjectDeserializer(Deserializer<Object> objectDeserializer) implements Deserializer<Object> {

        @Override
        public Deserializer<Object> createSpecific(DecoderContext context, Argument<? super Object> type) throws SerdeException {
            if (type.equalsType(Argument.OBJECT_ARGUMENT)) {
                return objectDeserializer.createSpecific(context, type);
            }
            SpecificSerdeTracker.markContextBound(context);
            return boundDeserializer();
        }

        @Override
        public Object deserialize(Decoder decoder, DecoderContext context, Argument<? super Object> type) {
            throw new IllegalStateException("The object deserializer of a probing context cannot deserialize");
        }
    }

    /**
     * The placeholder of a deserializer bound to the context in a specific serde created with a probing context,
     * which is never used to deserialize.
     */
    private static final class BoundDeserializer implements Deserializer<Object> {
        private static final BoundDeserializer INSTANCE = new BoundDeserializer();

        @Override
        public Object deserialize(Decoder decoder, DecoderContext context, Argument<? super Object> type) {
            throw new IllegalStateException("A specific deserializer bound to its context cannot be reused");
        }
    }

    /**
     * The placeholder of a serializer bound to the context in a specific serde created with a probing context,
     * which is never used to serialize.
     */
    private static final class BoundSerializer implements Serializer<Object> {
        private static final BoundSerializer INSTANCE = new BoundSerializer();

        @Override
        public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Object> type, Object value) {
            throw new IllegalStateException("A specific serializer bound to its context cannot be reused");
        }
    }

    private record Entry(Argument<?> type, Object value) {
    }

    private static final class Key {
        private final Argument<?> type;
        private final int hashCode;

        private Key(Argument<?> type) {
            this.type = type;
            this.hashCode = type.typeHashCode();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key key && sameArgument(type, key.type);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}
