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
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.exceptions.path.ReferencePath;
import io.micronaut.serde.support.util.SerdeArgumentConf;

import java.io.IOException;
import java.util.Map;

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

    AbstractMapObjectSerializer(Argument<? extends Map<K, V>> type, EncoderContext context) throws SerdeException {
        includeContent = type.getAnnotationMetadata()
            .enumValue(SerdeConfig.class.getName(), SerdeConfig.INCLUDE_CONTENT, SerdeConfig.SerInclude.class)
            .orElse(SerdeConfig.SerInclude.ALWAYS);
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

    protected abstract void encodeKey(Encoder encoder, EncoderContext context, K k) throws IOException;

    @Override
    public final void serialize(Encoder encoder, EncoderContext context, Argument<? extends Map<K, V>> type, Map<K, V> value) throws IOException {
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
    public final boolean isAbsent(EncoderContext context, Map<K, V> value) {
        return value == null;
    }

    @Override
    public final boolean isEmpty(EncoderContext context, Map<K, V> value) {
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
