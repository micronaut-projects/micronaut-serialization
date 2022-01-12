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
package io.micronaut.serde.support.serializers;

import java.io.IOException;
import java.util.Map;

import io.micronaut.context.annotation.Factory;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
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
                if (value instanceof String) {
                    encoder.encodeString((String) value);
                } else {
                    encoder.encodeString(value.toString());
                }
            }

            @Override
            public boolean isEmpty(CharSequence value) {
                return value == null || value.length() == 0;
            }
        };
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
     * A serializer for all instances of {@link java.lang.Boolean}.
     *
     * @return A boolean serializer
     */
    @Singleton
    protected Serializer<Boolean> booleanSerializer() {
        return (encoder, context, value, type) -> encoder.encodeBoolean(value);
    }

    /**
     * A serializer for maps.
     *
     * @param <K>  The key type
     * @param <V>  The value type
     * @return A bit decimal serializer
     */
    @Singleton
    protected <K, V> Serializer<Map<K, V>> mapSerializer() {
        return new Serializer<Map<K, V>>() {
            @Override
            public void serialize(Encoder encoder,
                                  EncoderContext context,
                                  Map<K, V> value,
                                  Argument<? extends Map<K, V>> type) throws IOException {
                final Encoder childEncoder = encoder.encodeObject(type);
                final Argument[] generics = type.getTypeParameters();
                final boolean hasGenerics = ArrayUtils.isNotEmpty(generics) && generics.length != 2;
                if (hasGenerics) {

                    final Argument<V> valueGeneric = (Argument<V>) generics[1];
                    final Serializer<V> valSerializer = (Serializer<V>) context.findSerializer(valueGeneric);
                    for (K k : value.keySet()) {
                        encodeMapKey(context, childEncoder, k);
                        final V v = value.get(k);
                        if (v == null) {
                            childEncoder.encodeNull();
                        } else {
                            valSerializer.serialize(
                                    childEncoder,
                                    context,
                                    v,
                                    valueGeneric
                            );
                        }
                    }
                } else {
                    // slow path, lookup each value serializer
                    for (Map.Entry<K, V> entry : value.entrySet()) {
                        encodeMapKey(context, childEncoder, entry.getKey());
                        final V v = entry.getValue();
                        if (v == null) {
                            childEncoder.encodeNull();
                        } else {
                            @SuppressWarnings("unchecked")
                            final Argument<V> valueGeneric = (Argument<V>) Argument.of(v.getClass());
                            final Serializer<? super V> valSerializer = context.findSerializer(valueGeneric);
                            valSerializer.serialize(
                                    childEncoder,
                                    context,
                                    v,
                                    valueGeneric
                            );
                        }
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

    private <K> void encodeMapKey(Serializer.EncoderContext context, Encoder childEncoder, K k) throws IOException {
        // relies on the key type implementing toString() correctly
        // perhaps we should supply conversion service
        if (k instanceof CharSequence) {
            childEncoder.encodeKey(k.toString());
        } else {
            try {
                final String result = context.getConversionService().convertRequired(
                        k,
                        Argument.STRING
                );
                childEncoder.encodeKey(result != null ? result : k.toString());
            } catch (ConversionErrorException e) {
                throw new SerdeException("Error converting Map key [" + k + "] to String: " + e.getMessage(), e);
            }
        }
    }

}
