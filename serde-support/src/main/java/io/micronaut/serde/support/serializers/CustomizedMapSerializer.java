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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.ObjectSerializer;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.SerializerRegistrar;
import io.micronaut.serde.support.util.JsonNodeEncoder;
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
@Internal
final class CustomizedMapSerializer<K, V> implements CustomizableSerializer<Map<K, V>>, SerializerRegistrar<Map<K, V>> {

    @Override
    public ObjectSerializer<Map<K, V>> createSpecific(EncoderContext context, Argument<? extends Map<K, V>> type) throws SerdeException {
        final Argument<?>[] generics = type.getTypeParameters();
        final boolean hasGenerics = ArrayUtils.isNotEmpty(generics) && generics.length == 2;
        if (hasGenerics) {
            final Argument<K> keyGeneric = (Argument<K>) generics[0];
            final Serializer<K> keySerializer = findKeySerializer(context, keyGeneric);
            final boolean isStringKey = keyGeneric.getType().equals(String.class) || CharSequence.class.isAssignableFrom(keyGeneric.getType());
            final Argument<V> valueGeneric = (Argument<V>) generics[1];
            final Serializer<V> valSerializer = (Serializer<V>) context.findSerializer(valueGeneric).createSpecific(context, valueGeneric);
            return new ObjectSerializer<>() {

                @Override
                public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Map<K, V>> type, Map<K, V> value) throws IOException {
                    final Encoder objectEncoder = encoder.encodeObject(type);
                    serializeInto(objectEncoder, context, type, value);
                    objectEncoder.finishStructure();
                }

                @Override
                public void serializeInto(Encoder encoder, EncoderContext context, Argument<? extends Map<K, V>> type, Map<K, V> value) throws IOException {
                    for (Map.Entry<K, V> entry : value.entrySet()) {
                        K k = entry.getKey();
                        if (k == null) {
                            encoder.encodeNull();
                        } else if (isStringKey) {
                            encoder.encodeKey(k.toString());
                        } else {
                            encodeMapKey(context, encoder, keyGeneric, keySerializer, k);
                        }
                        V v = entry.getValue();
                        if (v == null) {
                            encoder.encodeNull();
                        } else {
                            valSerializer.serialize(encoder, context, valueGeneric, v);
                        }
                    }
                }

                @Override
                public boolean isEmpty(EncoderContext context, Map<K, V> value) {
                    return CollectionUtils.isEmpty(value);
                }
            };
        } else {
            return new ObjectSerializer<>() {

                @Override
                public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Map<K, V>> type, Map<K, V> value) throws IOException {
                    // slow path, lookup each value serializer
                    final Encoder childEncoder = encoder.encodeObject(type);
                    serializeInto(childEncoder, context, type, value);
                    childEncoder.finishStructure();
                }

                @Override
                public void serializeInto(Encoder encoder, EncoderContext context, Argument<? extends Map<K, V>> type, Map<K, V> value) throws IOException {
                    Argument<K> keyGeneric = null;
                    Serializer<? super K> keySerializer = null;
                    Argument<V> valueGeneric = null;
                    Serializer<? super V> valSerializer = null;
                    for (Map.Entry<K, V> entry : value.entrySet()) {
                        K k = entry.getKey();
                        if (k instanceof CharSequence) {
                            encoder.encodeKey(k.toString());
                        } else {
                            if (keyGeneric == null || !keyGeneric.getType().equals(k.getClass())) {
                                keyGeneric = (Argument<K>) Argument.of(k.getClass());
                                keySerializer = findKeySerializer(context, keyGeneric);
                            }
                            encodeMapKey(context, encoder, keyGeneric, keySerializer, k);
                        }
                        final V v = entry.getValue();
                        if (v == null) {
                            encoder.encodeNull();
                        } else {
                            if (valueGeneric == null || !valueGeneric.getType().equals(v.getClass())) {
                                valueGeneric = (Argument<V>) Argument.of(v.getClass());
                                valSerializer = context.findSerializer(valueGeneric).createSpecific(context, valueGeneric);
                            }
                            valSerializer.serialize(encoder, context, valueGeneric, v);
                        }
                    }
                }

                @Override
                public boolean isEmpty(EncoderContext context, Map<K, V> value) {
                    return CollectionUtils.isEmpty(value);
                }
            };
        }
    }

    private Serializer<K> findKeySerializer(EncoderContext context, Argument<K> keyGeneric) throws SerdeException {
        try {
            return (Serializer<K>) context.findSerializer(keyGeneric).createSpecific(context, keyGeneric);
        } catch (SerdeException e) {
            if (e.getCause() instanceof IntrospectionException) {
                // The key is not introspected
                return (encoder, ctx, type, value) -> convertMapKeyToStringAndEncode(ctx, encoder, value);
            }
            throw e;
        }
    }

    private void encodeMapKey(EncoderContext context,
                              Encoder encoder,
                              Argument<K> keyGeneric,
                              Serializer<? super K> keySerializer,
                              K k) throws IOException {
        JsonNodeEncoder keyEncoder = JsonNodeEncoder.create();
        try {
            keySerializer.serialize(keyEncoder, context, keyGeneric, k);
        } catch (SerdeException e) {
            if (e.getCause() instanceof IntrospectionException) {
                // The key is not introspected
                convertMapKeyToStringAndEncode(context, encoder, k);
                return;
            }
            throw e;
        }
        JsonNode keyNode = keyEncoder.getCompletedValue();
        if (keyNode.isString()) {
            encoder.encodeKey(keyNode.getStringValue());
        } else if (keyNode.isNull()) {
            throw new SerdeException("Null key for a Map not allowed in JSON");
        } else if (keyNode.isBoolean() || keyNode.isNumber()) {
            encoder.encodeKey(keyNode.coerceStringValue());
        } else {
            convertMapKeyToStringAndEncode(context, encoder, keyNode.getValue());
        }
    }

    private void convertMapKeyToStringAndEncode(EncoderContext context, Encoder encoder, Object keyValue) throws IOException {
        try {
            final String result = context.getConversionService().convertRequired(keyValue, Argument.STRING);
            if (result == null) {
                throw new SerdeException("Null key for a Map not allowed in JSON");
            }
            encoder.encodeKey(result);
        } catch (ConversionErrorException ce) {
            throw new SerdeException("Error converting Map key [" + keyValue + "] to String: " + ce.getMessage(), ce);
        }
    }

    @Override
    public Argument<Map<K, V>> getType() {
        return (Argument) Argument.mapOf(Argument.ofTypeVariable(Object.class, "K"), Argument.ofTypeVariable(Object.class, "V"));
    }
}
