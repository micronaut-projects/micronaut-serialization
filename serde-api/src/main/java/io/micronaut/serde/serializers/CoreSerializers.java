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
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import io.micronaut.context.annotation.Factory;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import jakarta.inject.Singleton;

/**
 * Factory class for core serializers.
 */
@Factory
public class CoreSerializers {

    /**
     * A serializer for the date type.
     *
     * @return A date serializer
     */
    @Singleton
    protected Serializer<Date> dateSerializer() {
        return (encoder, context, value, type) -> {
            context.findSerializer(Argument.LONG)
                    .serialize(
                            encoder,
                            context, value.getTime(),
                            Argument.LONG
                    );
        };
    }

    /**
     * A serializer for all instances of {@link java.lang.CharSequence}.
     *
     * @return A char sequence serializer
     */
    @Singleton
    protected Serializer<CharSequence> charSequenceSerializer() {
        return new Serializer<CharSequence>() {
            @Override
            public void serialize(Encoder encoder,
                                  EncoderContext context,
                                  CharSequence value,
                                  Argument<? extends CharSequence> type) throws IOException {
                encoder.encodeString(value.toString());
            }

            @Override
            public boolean isEmpty(CharSequence value) {
                return value == null || value.length() == 0;
            }
        };
    }

    /**
     * A serializer for all instances of {@link java.lang.Double}.
     *
     * @return A double serializer
     */
    @Singleton
    protected Serializer<Double> doubleSerializer() {
        return (encoder, context, value, type) -> encoder.encodeDouble(value);
    }

    /**
     * A serializer for all instances of {@link java.lang.Float}.
     *
     * @return A float serializer
     */
    @Singleton
    protected Serializer<Float> floatSerializer() {
        return (encoder, context, value, type) -> encoder.encodeFloat(value);
    }

    /**
     * A serializer for all instances of {@link java.lang.Short}.
     *
     * @return A short serializer
     */
    @Singleton
    protected Serializer<Short> shortSerializer() {
        return (encoder, context, value, type) -> encoder.encodeShort(value);
    }

    /**
     * A serializer for all instances of {@link java.lang.Character}.
     *
     * @return A Character serializer
     */
    @Singleton
    protected Serializer<Character> charSerializer() {
        return (encoder, context, value, type) -> encoder.encodeChar(value);
    }

    /**
     * A serializer for all instances of {@link java.lang.Byte}.
     *
     * @return A byte serializer
     */
    @Singleton
    protected Serializer<Byte> byteSerializer() {
        return (encoder, context, value, type) -> encoder.encodeByte(value);
    }

    /**
     * A serializer for all instances of {@link java.lang.Boolean}.
     *
     * @return A boolean serializer
     */
    @Singleton
    protected Serializer<Boolean> booleanSerializer() {
        return (encoder, context, value, type) -> encoder.encodeBoolean(value);
    }

    /**
     * A serializer for all instances of {@link java.lang.Iterable}.
     *
     * @param <T> The element type
     * @return An iterable serializer
     */
    @Singleton
    protected <T> Serializer<Iterable<T>> iterableSerializer() {
        return new Serializer<Iterable<T>>() {
            @Override
            public void serialize(Encoder encoder,
                                  EncoderContext context,
                                  Iterable<T> value,
                                  Argument<? extends Iterable<T>> type) throws IOException {
                final Encoder childEncoder = encoder.encodeArray();
                final Argument[] generics = type.getTypeParameters();
                if (ArrayUtils.isEmpty(generics)) {
                    throw new SerdeException("Serializing raw iterables is not supported for value: " + value);
                }
                final Argument<T> generic = (Argument<T>) generics[0];
                final Serializer<T> componentSerializer = (Serializer<T>) context.findSerializer(generic);
                for (T t : value) {
                    componentSerializer.serialize(
                            childEncoder,
                            context,
                            t,
                            generic
                    );
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
        };
    }

    /**
     * A serializer for all instances of {@link java.util.Optional}.
     *
     * @param <T> The optional type
     * @return An Optional serializer
     */
    @Singleton
    protected <T> Serializer<Optional<T>> optionalSerializer() {
        return new Serializer<Optional<T>>() {
            @Override
            public void serialize(Encoder encoder,
                                  EncoderContext context,
                                  Optional<T> value,
                                  Argument<? extends Optional<T>> type) throws IOException {
                final Argument[] generics = type.getTypeParameters();
                if (ArrayUtils.isEmpty(generics)) {
                    throw new SerdeException("Serializing raw optionals is not supported for value: " + value);
                }
                final T o = value.orElse(null);
                if (o != null) {
                    final Argument<T> generic = (Argument<T>) generics[0];
                    final Serializer<T> componentSerializer = (Serializer<T>) context.findSerializer(generic);
                    componentSerializer.serialize(
                            encoder,
                            context,
                            o,
                            generic
                    );
                } else {
                    encoder.encodeNull();
                }
            }

            @Override
            public boolean isEmpty(Optional<T> value) {
                return value == null || !value.isPresent();
            }

            @Override
            public boolean isAbsent(Optional<T> value) {
                return value == null || !value.isPresent();
            }
        };
    }

    /**
     * A serializer for maps.
     *
     * @param <K>  The key type
     * @param <V>  The value type
     * @return A bit decimal serializer
     */
    @Singleton
    protected <K extends CharSequence, V> Serializer<Map<K, V>> mapSerializer() {
        return new Serializer<Map<K, V>>() {
            @Override
            public void serialize(Encoder encoder,
                                  EncoderContext context,
                                  Map<K, V> value,
                                  Argument<? extends Map<K, V>> type) throws IOException {
                final Encoder childEncoder = encoder.encodeObject();
                final Argument[] generics = type.getTypeParameters();
                if (ArrayUtils.isEmpty(generics) || generics.length != 2) {
                    throw new SerdeException("Serializing raw maps is not supported for value: " + value);
                }
                final Argument<V> valueGeneric = (Argument<V>) generics[1];
                final Serializer<V> valSerializer = (Serializer<V>) context.findSerializer(valueGeneric);
                for (K k : value.keySet()) {
                    childEncoder.encodeKey(k.toString());
                    final V v = value.get(k);
                    if (v == null) {
                        childEncoder.encodeNull();
                    } else {
                        valSerializer.serialize(
                                encoder,
                                context,
                                v,
                                valueGeneric
                        );
                    }
                }
                childEncoder.finishStructure();
            }

            @Override
            public boolean isEmpty(Map<K, V> value) {
                return CollectionUtils.isEmpty(value);
            }
        };
    }
}
