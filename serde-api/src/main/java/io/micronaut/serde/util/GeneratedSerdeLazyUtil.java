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
package io.micronaut.serde.util;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Lazy property serdes used by generated serdes for properties whose type can refer back to the
 * generated type, such as {@code List<Self>}. Generated serdes resolve their property serdes on
 * construction and the registry creates a new generated serde for every specific lookup, so
 * resolving such a property eagerly would construct the owning serde again without end.
 *
 * <p>The delegate is resolved once, on first use, and cached.</p>
 *
 * @since 3.2.2
 */
@Internal
@UsedByGeneratedCode
public final class GeneratedSerdeLazyUtil {

    private GeneratedSerdeLazyUtil() {
    }

    /**
     * Creates a serializer that resolves the specific serializer for the given type on first use.
     *
     * @param context The encoder context
     * @param type    The property type
     * @return The lazy serializer
     */
    public static Serializer<?> lazySerializer(Serializer.EncoderContext context, Argument<?> type) {
        return new LazySerializer<>(context, type);
    }

    /**
     * Creates a deserializer that resolves the specific deserializer for the given type on first use.
     *
     * @param context The decoder context
     * @param type    The property type
     * @return The lazy deserializer
     */
    public static Deserializer<?> lazyDeserializer(Deserializer.DecoderContext context, Argument<?> type) {
        return new LazyDeserializer<>(context, type);
    }

    private static final class LazySerializer<T> implements Serializer<T> {
        private final Serializer.EncoderContext context;
        private final Argument<T> type;
        private volatile @Nullable Serializer<T> delegate;

        @SuppressWarnings("unchecked")
        private LazySerializer(Serializer.EncoderContext context, Argument<?> type) {
            this.context = context;
            this.type = (Argument<T>) type;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private Serializer<T> delegate() throws SerdeException {
            Serializer<T> serializer = delegate;
            if (serializer == null) {
                serializer = (Serializer<T>) context.findSerializer(type).createSpecific(context, (Argument) type);
                delegate = serializer;
            }
            return serializer;
        }

        private Serializer<T> uncheckedDelegate() {
            try {
                return delegate();
            } catch (SerdeException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void serialize(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException {
            delegate().serialize(encoder, context, type, value);
        }

        @Override
        public boolean isEmpty(EncoderContext context, @Nullable T value) {
            return uncheckedDelegate().isEmpty(context, value);
        }

        @Override
        public boolean isAbsent(EncoderContext context, @Nullable T value) {
            return uncheckedDelegate().isAbsent(context, value);
        }

        @Override
        public boolean isDefault(EncoderContext context, T value) {
            return uncheckedDelegate().isDefault(context, value);
        }
    }

    private static final class LazyDeserializer<T> implements Deserializer<T> {
        private final Deserializer.DecoderContext context;
        private final Argument<T> type;
        private volatile @Nullable Deserializer<T> delegate;

        @SuppressWarnings("unchecked")
        private LazyDeserializer(Deserializer.DecoderContext context, Argument<?> type) {
            this.context = context;
            this.type = (Argument<T>) type;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private Deserializer<T> delegate() throws SerdeException {
            Deserializer<T> deserializer = delegate;
            if (deserializer == null) {
                deserializer = (Deserializer<T>) context.findDeserializer(type).createSpecific(context, (Argument) type);
                delegate = deserializer;
            }
            return deserializer;
        }

        @Override
        public T deserialize(Decoder decoder, DecoderContext context, Argument<? super T> type) throws IOException {
            return delegate().deserialize(decoder, context, type);
        }

        @Override
        public @Nullable T deserializeNullable(Decoder decoder, DecoderContext context, Argument<? super T> type) throws IOException {
            return delegate().deserializeNullable(decoder, context, type);
        }

        @Override
        public @Nullable T getDefaultValue(DecoderContext context, Argument<? super T> type) {
            try {
                return delegate().getDefaultValue(context, type);
            } catch (SerdeException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
