/*
 * Copyright 2017-2025 original authors
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
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.util.JsonNodeEncoder;

import java.io.IOException;
import java.util.Map;

/**
 * The runtime key map serializer.
 *
 * @param <K> The key type
 * @param <V> The value type
 * @author Denis Stepanov
 */
@Internal
final class RuntimeMapSerializer<K, V> extends AbstractMapObjectSerializer<K, V> {

    private final boolean isStringKey;
    private final Argument<K> keyGeneric;
    private final Serializer<K> keySerializer;

    RuntimeMapSerializer(Argument<? extends Map<K, V>> type, EncoderContext context) throws SerdeException {
        super(type, context);
        final Argument<?>[] generics = type.getTypeParameters();
        final boolean hasGenerics = ArrayUtils.isNotEmpty(generics) && generics.length == 2;
        if (hasGenerics) {
            keyGeneric = (Argument<K>) generics[0];
            isStringKey = keyGeneric.getType() == String.class || CharSequence.class.isAssignableFrom(keyGeneric.getType());
        } else {
            keyGeneric = (Argument<K>) Argument.OBJECT_ARGUMENT;
            isStringKey = false;
        }
        keySerializer = findKeySerializer(context, keyGeneric);
    }

    @Override
    protected void encodeKey(Encoder encoder, EncoderContext context, K k) throws IOException {
        if (k == null) {
            encoder.encodeNull();
        } else if (isStringKey || k instanceof CharSequence) {
            encoder.encodeKey(k.toString());
        } else {
            encodeMapKey(context, encoder, keyGeneric, keySerializer, k);
        }
    }

    private static <K> Serializer<K> findKeySerializer(EncoderContext context, Argument<K> keyGeneric) throws
        SerdeException {
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

    private static <K> void encodeMapKey(EncoderContext context,
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

    private static void convertMapKeyToStringAndEncode(EncoderContext context, Encoder
        encoder, Object keyValue) throws IOException {
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
}
