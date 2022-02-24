/*
 * Copyright 2017-2022 original authors
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

import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.util.CustomizableSerializer;

import java.io.IOException;
import java.util.Map;

/**
 * The map serializer.
 *
 * @param <K> The key type
 * @param <V> The value type
 * @author Denis Stepanov
 */
final class CustomizedMapSerializer<K, V> implements CustomizableSerializer<Map<K, V>> {

    @Override
    public Serializer<Map<K, V>> createSpecific(EncoderContext context, Argument<? extends Map<K, V>> type) throws SerdeException {
        final Argument[] generics = type.getTypeParameters();
        final boolean hasGenerics = ArrayUtils.isNotEmpty(generics) && generics.length != 2;
        if (hasGenerics) {
            final Argument<V> valueGeneric = (Argument<V>) generics[1];
            final Serializer<V> valSerializer = (Serializer<V>) context.findSerializer(valueGeneric).createSpecific(context, valueGeneric);
            return new Serializer<Map<K, V>>() {
                @Override
                public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Map<K, V>> type, Map<K, V> value) throws IOException {
                    final Encoder childEncoder = encoder.encodeObject(type);
                    for (K k : value.keySet()) {
                        encodeMapKey(context, childEncoder, k);
                        final V v = value.get(k);
                        if (v == null) {
                            childEncoder.encodeNull();
                        } else {
                            valSerializer.serialize(
                                    childEncoder,
                                    context,
                                    valueGeneric, v
                            );
                        }
                    }
                    childEncoder.finishStructure();
                }

                @Override
                public boolean isEmpty(EncoderContext context, Map<K, V> value) {
                    return CollectionUtils.isEmpty(value);
                }
            };
        } else {
            return new Serializer<Map<K, V>>() {
                @Override
                public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Map<K, V>> type, Map<K, V> value) throws IOException {
                    // slow path, lookup each value serializer
                    final Encoder childEncoder = encoder.encodeObject(type);
                    for (Map.Entry<K, V> entry : value.entrySet()) {
                        encodeMapKey(context, childEncoder, entry.getKey());
                        final V v = entry.getValue();
                        if (v == null) {
                            childEncoder.encodeNull();
                        } else {
                            @SuppressWarnings("unchecked") final Argument<V> valueGeneric = (Argument<V>) Argument.of(v.getClass());
                            final Serializer<? super V> valSerializer = context.findSerializer(valueGeneric)
                                .createSpecific(context, valueGeneric);
                            valSerializer.serialize(
                                    childEncoder,
                                    context,
                                    valueGeneric, v
                            );
                        }
                    }
                    childEncoder.finishStructure();
                }

                @Override
                public boolean isEmpty(EncoderContext context, Map<K, V> value) {
                    return CollectionUtils.isEmpty(value);
                }
            };
        }
    }

    private void encodeMapKey(EncoderContext context, Encoder childEncoder, K k) throws IOException {
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
