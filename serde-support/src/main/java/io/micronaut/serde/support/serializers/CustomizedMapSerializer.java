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
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.exceptions.path.ReferencePath;
import io.micronaut.serde.support.SerializerRegistrar;
import io.micronaut.serde.support.util.JsonNodeEncoder;
import io.micronaut.serde.support.util.SerdeArgumentConf;
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
        SerdeConfig.SerInclude includeContent = type.getAnnotationMetadata()
            .enumValue(SerdeConfig.class.getName(), SerdeConfig.INCLUDE_CONTENT, SerdeConfig.SerInclude.class)
            .orElse(SerdeConfig.SerInclude.ALWAYS);
        if (hasGenerics) {
            final Argument<K> keyGeneric = (Argument<K>) generics[0];
            final Serializer<K> keySerializer = findKeySerializer(context, keyGeneric);
            final boolean isStringKey = keyGeneric.getType().equals(String.class) || CharSequence.class.isAssignableFrom(keyGeneric.getType());
            // if there are annotations on the map property we need to combine the annotation metadata with the generic.
            @SuppressWarnings("unchecked")
            final Argument<V> valueGeneric = SerdeArgumentConf.reconstructGenericWithParentMetadata(type, (Argument<V>) generics[1]);

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
                        try {
                            V v = entry.getValue();

                            switch (includeContent) {
                                case NON_ABSENT:
                                    if (valSerializer.isAbsent(context, v)) {
                                        continue;
                                    }
                                    break;
                                case NON_EMPTY:
                                    if (valSerializer.isEmpty(context, v)) {
                                        continue;
                                    }
                                    break;
                                case NON_NULL:
                                    if (v == null) {
                                        continue;
                                    }
                                    break;
                                default:
                                    // fall through
                            }

                            if (k == null) {
                                encoder.encodeNull();
                            } else if (isStringKey) {
                                encoder.encodeKey(k.toString());
                            } else {
                                encodeMapKey(context, encoder, keyGeneric, keySerializer, k);
                            }
                            if (v == null) {
                                encoder.encodeNull();
                            } else {
                                valSerializer.serialize(encoder, context, valueGeneric, v);
                            }
                        } catch (SerdeException e) {
                            e.getPath().add(ReferencePath.ofMap(value.getClass(), type, k == null ? "<null>" : k.toString()));
                            throw e;
                        }
                    }
                }

                @Override
                public boolean isAbsent(EncoderContext context, Map<K, V> value) {
                    return value == null;
                }

                @Override
                public boolean isEmpty(EncoderContext context, Map<K, V> value) {
                    if (CollectionUtils.isEmpty(value)) {
                        return true;
                    }
                    if (includeContent != SerdeConfig.SerInclude.ALWAYS) {
                        for (V v : value.values()) {
                            switch (includeContent) {
                                case NON_ABSENT:
                                    if (!valSerializer.isAbsent(context, v)) {
                                        return false;
                                    }
                                    break;
                                case NON_EMPTY:
                                    if (!valSerializer.isEmpty(context, v)) {
                                        return false;
                                    }
                                    break;
                                case NON_NULL:
                                    if (v != null) {
                                        return false;
                                    }
                                    break;
                                default:
                                    return false;
                            }
                        }
                        return true;
                    }
                    return false;
                }
            };
        } else {
            return new ObjectSerializer<>() {

                Argument<K> keyGeneric = null;
                Serializer<? super K> internalKeySerializer = null;
                Argument<V> valueGeneric = null;
                Serializer<? super V> internalValSerializer = null;

                @Override
                public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Map<K, V>> type, Map<K, V> value) throws IOException {
                    // slow path, lookup each value serializer
                    final Encoder childEncoder = encoder.encodeObject(type);
                    serializeInto(childEncoder, context, type, value);
                    childEncoder.finishStructure();
                }

                @Override
                public void serializeInto(Encoder encoder, EncoderContext context, Argument<? extends Map<K, V>> type, Map<K, V> value) throws IOException {
                    for (Map.Entry<K, V> entry : value.entrySet()) {
                        K k = entry.getKey();
                        try {
                            if (k instanceof CharSequence) {
                                encoder.encodeKey(k.toString());
                            } else {
                                Serializer<? super K> keySerializer = getKeySerializer(context, k);
                                encodeMapKey(context, encoder, keyGeneric, keySerializer, k);
                            }
                            final V v = entry.getValue();
                            if (v == null) {
                                encoder.encodeNull();
                            } else {
                                Serializer<? super V> valSerializer = getValueSerializer(context, v);
                                valSerializer.serialize(encoder, context, valueGeneric, v);
                            }
                        } catch (SerdeException e) {
                            e.getPath().add(ReferencePath.ofMap(value.getClass(), type, k.toString()));
                            throw e;
                        }
                    }
                }

                private Serializer<? super K> getKeySerializer(EncoderContext context, K k) throws SerdeException {
                    if (keyGeneric == null || !keyGeneric.getType().equals(k.getClass())) {
                        keyGeneric = (Argument<K>) Argument.of(k.getClass());
                        internalKeySerializer = findKeySerializer(context, keyGeneric);
                    }
                    return internalKeySerializer;
                }

                private synchronized Serializer<? super V> getValueSerializer(EncoderContext context, V v) throws SerdeException {
                    if (valueGeneric == null || !valueGeneric.getType().equals(v.getClass())) {
                        valueGeneric = (Argument<V>) Argument.of(v.getClass());
                        internalValSerializer = context.findSerializer(valueGeneric).createSpecific(context, valueGeneric);
                    }
                    return internalValSerializer;
                }

                @Override
                public boolean isAbsent(EncoderContext context, Map<K, V> value) {
                    return value == null;
                }

                @Override
                public boolean isEmpty(EncoderContext context, Map<K, V> value) {
                    if (CollectionUtils.isEmpty(value)) {
                        return true;
                    }
                    if (includeContent != SerdeConfig.SerInclude.ALWAYS) {
                        for (V v : value.values()) {
                            try {
                                Serializer<? super V> valueSerializer = getValueSerializer(context, v);
                                switch (includeContent) {
                                    case NON_ABSENT:
                                        if (!valueSerializer.isAbsent(context, v)) {
                                            return false;
                                        }
                                        break;
                                    case NON_EMPTY:
                                        if (!valueSerializer.isEmpty(context, v)) {
                                            return false;
                                        }
                                        break;
                                    case NON_NULL:
                                        if (v != null) {
                                            return false;
                                        }
                                        break;
                                    default:
                                        return false;
                                }
                            } catch (SerdeException e) {
                                return sneakyThrow(e);
                            }
                        }
                        return true;
                    }
                    return false;
                }
            };
        }
    }

    private static <T extends Throwable, R> R sneakyThrow(Throwable t) throws T {
        throw (T) t;
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
