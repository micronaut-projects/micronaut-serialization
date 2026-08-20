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
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.ObjectSerializer;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.XmlEncoder;
import io.micronaut.serde.config.SerializationConfiguration;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.exceptions.path.ReferencePath;
import io.micronaut.serde.support.util.SerdeArgumentConf;
import io.micronaut.serde.support.util.SerdeFeatures;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Map;
import javax.xml.namespace.QName;

/**
 * The abstract map serializer.
 *
 * @param <K> The key type
 * @param <V> The value type
 * @author Denis Stepanov
 */
@Internal
abstract sealed class AbstractMapObjectSerializer<K, V> implements ObjectSerializer<Map<K, V>> permits CharSequenceKeyMapSerializer, RuntimeMapSerializer, StringKeyMapSerializer {

    protected final SerdeConfig.SerInclude includeContent;
    private final Argument<V> valueGeneric;
    private final Serializer<V> valueSerializer;
    private final boolean sortMapEntries;
    private final boolean xmlAnyAttribute;

    AbstractMapObjectSerializer(Argument<? extends Map<K, V>> type, EncoderContext context) throws SerdeException {
        context = SerdeFeatures.withFeatures(context, type.getAnnotationMetadata());
        includeContent = type.getAnnotationMetadata()
            .enumValue(SerdeConfig.class.getName(), SerdeConfig.INCLUDE_CONTENT, SerdeConfig.SerInclude.class)
            .orElse(SerdeConfig.SerInclude.ALWAYS);
        sortMapEntries = context.getFeatures().contains(SerializationConfiguration.Feature.WRITE_SORTED_MAP_ENTRIES);
        xmlAnyAttribute = type.getAnnotationMetadata()
            .booleanValue(SerdeConfig.class, SerdeConfig.XML_ANY_ATTRIBUTE_PROPERTY)
            .orElse(false);
        final Argument<?>[] generics = type.getTypeParameters();
        final boolean hasGenerics = ArrayUtils.isNotEmpty(generics) && generics.length == 2;
        if (hasGenerics) {
            // if there are annotations on the map property we need to combine the annotation metadata with the generic.
            valueGeneric = SerdeArgumentConf.reconstructGenericWithParentMetadata(type, (Argument<V>) generics[1]);
        } else {
            valueGeneric = SerdeArgumentConf.reconstructGenericWithParentMetadata(type, (Argument<V>) Argument.OBJECT_ARGUMENT);
        }
        valueSerializer = (Serializer<V>) context.findSerializer(valueGeneric).createSpecific(context, valueGeneric);
    }

    private static int compareEntriesByKey(Map.Entry<?, ?> left, Map.Entry<?, ?> right) {
        return compareKeys(left.getKey(), right.getKey());
    }

    @SuppressWarnings("unchecked")
    private static int compareKeys(@Nullable Object left, @Nullable Object right) {
        if (left == right) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        if (left instanceof Comparable<?> && left.getClass() == right.getClass()) {
            return ((Comparable<Object>) left).compareTo(right);
        }
        return String.valueOf(left).compareTo(String.valueOf(right));
    }

    protected abstract void encodeKey(Encoder encoder, EncoderContext context, K k) throws IOException;

    protected final void encodeMapKey(Encoder encoder, String key) throws IOException {
        if (xmlAnyAttribute && encoder instanceof XmlEncoder xmlEncoder) {
            xmlEncoder.encodeAttributeKey(new QName(key));
        } else {
            encoder.encodeKey(key);
        }
    }

    protected final boolean isXmlAnyAttribute() {
        return xmlAnyAttribute;
    }

    @Override
    public final void serialize(Encoder encoder, EncoderContext context, Argument<? extends Map<K, V>> type, Map<K, V> value) throws IOException {
        final Encoder objectEncoder = encoder.encodeObject(type);
        serializeInto(objectEncoder, context, type, value);
        objectEncoder.finishStructure();
    }

    @Override
    public void serializeInto(Encoder encoder, EncoderContext context, Argument<? extends Map<K, V>> type, Map<K, V> value) throws IOException {
        Iterable<Map.Entry<K, V>> entries = value.entrySet();
        if (sortMapEntries) {
            entries = value.entrySet()
                .stream()
                .sorted(AbstractMapObjectSerializer::compareEntriesByKey)
                .toList();
        }
        for (Map.Entry<@Nullable K, @Nullable V> entry : entries) {
            K k = entry.getKey();
            try {
                V v = entry.getValue();

                switch (includeContent) {
                    case NON_ABSENT:
                        if (valueSerializer.isAbsent(context, v)) {
                            continue;
                        }
                        break;
                    case NON_EMPTY:
                        if (valueSerializer.isEmpty(context, v)) {
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
                } else {
                    encodeKey(encoder, context, k);
                }
                if (v == null) {
                    encoder.encodeNull();
                } else {
                    valueSerializer.serialize(encoder, context, valueGeneric, v);
                }
            } catch (SerdeException e) {
                e.getPath().add(ReferencePath.ofMap(value.getClass(), type, k == null ? "<null>" : k.toString()));
                throw e;
            }
        }
    }

    @Override
    public final boolean isAbsent(EncoderContext context, @Nullable Map<K, V> value) {
        return value == null;
    }

    @Override
    public final boolean isEmpty(EncoderContext context, @Nullable Map<K, V> value) {
        if (CollectionUtils.isEmpty(value)) {
            return true;
        }
        if (includeContent != SerdeConfig.SerInclude.ALWAYS) {
            for (V v : value.values()) {
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
            }
            return true;
        }
        return false;
    }

}
