/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.serde.serializers;

import java.io.IOException;
import java.util.Collection;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import jakarta.inject.Singleton;

/**
 * A serializer for any iterable.
 * @param <T> The generic type
 */
@Singleton
final class IterableSerializer<T> implements Serializer<Iterable<T>> {
    @Override
    public Serializer<Iterable<T>> createSpecific(Argument<? extends Iterable<T>> type, EncoderContext encoderContext)
            throws SerdeException {
        final Argument<?>[] generics = type.getTypeParameters();
        if (generics.length > 0) {

            @SuppressWarnings("unchecked") final Argument<T> generic = (Argument<T>) generics[0];
            Serializer<? super T> componentSerializer = encoderContext.findSerializer(generic);
            return new InnerIterableSerializer(generic, componentSerializer);
        }
        return this;
    }

    @Override
    public void serialize(Encoder encoder,
                          EncoderContext context,
                          Iterable<T> value,
                          Argument<? extends Iterable<T>> type) throws IOException {
        final Encoder childEncoder = encoder.encodeArray(type);
        final Argument<?>[] generics = type.getTypeParameters();
        if (generics.length > 0) {
            @SuppressWarnings("unchecked") final Argument<T> generic = (Argument<T>) generics[0];
            Serializer<? super T> componentSerializer = context.findSerializer(generic);

            for (T t : value) {
                if (t == null) {
                    encoder.encodeNull();
                } else {
                    componentSerializer.serialize(
                            childEncoder,
                            context,
                            t,
                            generic
                    );
                }
            }
        } else {
            // slow path, generic look up per element
            for (T t : value) {
                if (t == null) {
                    encoder.encodeNull();
                    continue;
                }
                @SuppressWarnings("unchecked")
                final Argument<T> generic = (Argument<T>) Argument.of(t.getClass());
                Serializer<? super T> componentSerializer = context.findSerializer(generic);
                componentSerializer.serialize(
                        childEncoder,
                        context,
                        t,
                        generic
                );
            }
        }
        childEncoder.finishStructure();
    }

    @Override
    public boolean isEmpty(Iterable<T> value) {
        if (value == null) {
            return true;
        }
        if (value instanceof Collection) {
            return ((Collection<T>) value).isEmpty();
        } else {
            return !value.iterator().hasNext();
        }
    }

    private final class InnerIterableSerializer implements Serializer<Iterable<T>> {
        private final Argument<T> generic;
        private final Serializer<? super T> componentSerializer;

        private InnerIterableSerializer(Argument<T> generic, Serializer<? super T> componentSerializer) {
            this.generic = generic;
            this.componentSerializer = componentSerializer;
        }

        @Override
        public void serialize(Encoder encoder, EncoderContext context, Iterable<T> value, Argument<? extends Iterable<T>> type)
                throws IOException {
            try (Encoder array = encoder.encodeArray(type)) {
                for (T t : value) {
                    if (t == null) {
                        encoder.encodeNull();
                    } else {
                        componentSerializer.serialize(
                                array,
                                context,
                                t,
                                generic
                        );
                    }
                }
            }
        }

        @Override
        public boolean isEmpty(Iterable<T> value) {
            return IterableSerializer.this.isEmpty(value);
        }

        @Override
        public boolean isAbsent(Iterable<T> value) {
            return IterableSerializer.this.isAbsent(value);
        }
    }
}
