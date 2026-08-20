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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.XmlEncoder;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.util.JsonNodeEncoder;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import javax.xml.namespace.QName;

/**
 * The runtime key map serializer.
 *
 * @param <K> The key type
 * @param <V> The value type
 * @author Denis Stepanov
 */
@Internal
final class RuntimeMapSerializer<K, V> extends AbstractMapObjectSerializer<K, V> {
    private static final AnnotationMetadata MAP_KEY_SERIALIZATION_METADATA = mapKeySerializationMetadata();

    private final boolean isStringKey;
    private final Argument<K> keyGeneric;
    private final Serializer<K> keySerializer;

    RuntimeMapSerializer(Argument<? extends Map<K, V>> type, EncoderContext context) throws SerdeException {
        super(type, context);
        final Argument<?>[] generics = type.getTypeParameters();
        final boolean hasGenerics = ArrayUtils.isNotEmpty(generics) && generics.length == 2;
        if (hasGenerics) {
            keyGeneric = asMapKeyArgument((Argument<K>) generics[0]);
            isStringKey = keyGeneric.getType() == String.class || CharSequence.class.isAssignableFrom(keyGeneric.getType());
        } else {
            keyGeneric = asMapKeyArgument((Argument<K>) Argument.OBJECT_ARGUMENT);
            isStringKey = false;
        }
        keySerializer = findKeySerializer(context, keyGeneric);
    }

    @Override
    protected void encodeKey(Encoder encoder, EncoderContext context, K k) throws IOException {
        if (isXmlAnyAttribute() && k instanceof QName qName) {
            if (encoder instanceof XmlEncoder xmlEncoder) {
                xmlEncoder.encodeAttributeKey(qName);
            } else {
                encodeMapKey(encoder, qName.getLocalPart());
            }
            return;
        }
        if (isStringKey || k instanceof CharSequence) {
            encodeMapKey(encoder, k.toString());
        } else {
            encodeMapKey(context, encoder, keyGeneric, keySerializer, k);
        }
    }

    private static AnnotationMetadata mapKeySerializationMetadata() {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        metadata.addDeclaredAnnotation(SerdeConfig.SerKey.class.getName(), Map.of());
        return metadata;
    }

    private static <K> Argument<K> asMapKeyArgument(Argument<K> keyGeneric) {
        AnnotationMetadata annotationMetadata = keyGeneric.getAnnotationMetadata();
        if (annotationMetadata.hasAnnotation(SerdeConfig.SerKey.class)) {
            return keyGeneric;
        }
        AnnotationMetadata keyAnnotationMetadata = annotationMetadata.isEmpty()
            ? MAP_KEY_SERIALIZATION_METADATA
            : new AnnotationMetadataHierarchy(annotationMetadata, MAP_KEY_SERIALIZATION_METADATA);
        return keyGeneric.withAnnotationMetadata(keyAnnotationMetadata);
    }

    private static <K> Serializer<K> findKeySerializer(EncoderContext context, Argument<K> keyGeneric) throws
        SerdeException {
        try {
            return (Serializer<K>) context.findSerializer(keyGeneric).createSpecific(context, keyGeneric);
        } catch (SerdeException e) {
            if (e.getCause() instanceof IntrospectionException) {
                // The key is not introspected
                return (encoder, ctx, type, value) -> encodeConvertedMapKey(ctx, encoder, value);
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
            encodeMapKey(encoder, keyNode.getStringValue());
        } else if (keyNode.isNull()) {
            throw new SerdeException("Null key for a Map not allowed in JSON");
        } else if (keyNode.isBoolean() || keyNode.isNumber()) {
            encodeMapKey(encoder, keyNode.coerceStringValue());
        } else {
            convertMapKeyToStringAndEncode(context, encoder, Objects.requireNonNull(keyNode.getValue()));
        }
    }

    private void convertMapKeyToStringAndEncode(EncoderContext context, Encoder encoder, Object keyValue) throws IOException {
        try {
            encodeMapKey(encoder, context.getConversionService().convertRequired(keyValue, Argument.STRING));
        } catch (ConversionErrorException ce) {
            throw new SerdeException("Error converting Map key [" + keyValue + "] to String: " + ce.getMessage(), ce);
        }
    }

    private static void encodeConvertedMapKey(EncoderContext context, Encoder encoder, Object keyValue) throws IOException {
        try {
            encoder.encodeKey(context.getConversionService().convertRequired(keyValue, Argument.STRING));
        } catch (ConversionErrorException ce) {
            throw new SerdeException("Error converting Map key [" + keyValue + "] to String: " + ce.getMessage(), ce);
        }
    }
}
