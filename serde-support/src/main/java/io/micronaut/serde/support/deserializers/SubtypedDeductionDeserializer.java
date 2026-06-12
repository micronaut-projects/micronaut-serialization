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
package io.micronaut.serde.support.deserializers;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Keys;
import io.micronaut.serde.KeysAwareDecoder;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Subtyped deduction deserializer.
 *
 * @author Denis Stepanov
 * @since 2.15
 */
@Internal
final class SubtypedDeductionDeserializer implements Deserializer<Object> {

    private final DeserBean<? super Object> deserBean;
    private final DeserBeanSubtypeInfo<? super Object> subtypeInfo;
    private final Map<String, Deserializer<Object>> deserializers;
    private final List<String> propertyNames;
    private final Keys propertyKeys;

    SubtypedDeductionDeserializer(DeserBean<? super Object> deserBean,
                                  Map<String, Deserializer<Object>> deserializers) {
        this.deserBean = deserBean;
        this.subtypeInfo = Objects.requireNonNull(deserBean.subtypeInfo);
        this.deserializers = deserializers;
        this.propertyNames = subtypePropertyKeys(subtypeInfo);
        this.propertyKeys = Keys.create(propertyNames, deserBean.acceptCaseInsensitiveProperties);
        SerdeConfig.SerSubtyped.DiscriminatorType discriminatorType = subtypeInfo.info().discriminatorType();
        if (discriminatorType != SerdeConfig.SerSubtyped.DiscriminatorType.PROPERTY
            && discriminatorType != SerdeConfig.SerSubtyped.DiscriminatorType.EXISTING_PROPERTY) {
            throw new IllegalStateException("Unsupported discriminator type: " + discriminatorType);
        }
    }

    @Override
    public Object deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Object> type)
        throws IOException {
        try (DemuxingObjectDecoder.PrimedDecoder primed = DemuxingObjectDecoder.prime(decoder)) {
            Decoder typeFinder = primed.decodeObjectNonConsuming(type);
            Deserializer<Object> deserializer = findDeserializer(typeFinder);
            typeFinder.finishStructure(true);

            return deserializer.deserialize(
                primed,
                decoderContext,
                type
            );
        }
    }

    private Deserializer<Object> findDeserializer(Decoder objectDecoder) throws IOException {
        KeysAwareDecoder keysAwareDecoder = KeysAwareDecoder.of(objectDecoder);
        Map<String, DeserBeanSubtypeInfo.SubtypeDef<?>> subtypes = new LinkedHashMap<>(subtypeInfo.subtypes());

        while (true) {
            if (subtypes.size() == 1) {
                Deserializer<Object> deserializer = deserializers.get(subtypes.keySet().iterator().next());
                if (deserializer != null) {
                    return deserializer;
                }
                break;
            }
            if (subtypes.isEmpty()) {
                break;
            }
            final int keyIndex = keysAwareDecoder.decodeKey(propertyKeys);
            final String key;
            if (keyIndex == KeysAwareDecoder.MATCH_END_OBJECT) {
                break;
            } else if (keyIndex == KeysAwareDecoder.MATCH_UNKNOWN_NAME) {
                key = keysAwareDecoder.decodeKey();
                if (key == null) {
                    break;
                }
            } else {
                key = propertyNames.get(keyIndex);
            }
            Iterator<Map.Entry<String, DeserBeanSubtypeInfo.SubtypeDef<?>>> iterator = subtypes.entrySet().iterator();
            while (iterator.hasNext()) {
                DeserBean<?> subtype = iterator.next().getValue().deserBean();
                if (subtype == null) {
                    iterator.remove();
                    continue;
                }
                if (subtype.injectProperties != null && subtype.injectProperties.contains(key)) {
                    // Found property
                    continue;
                }
                if (subtype.creatorParams != null && subtype.creatorParams.contains(key)) {
                    // Found property
                    continue;
                }
                // Not found
                iterator.remove();
            }

            keysAwareDecoder.skipValue();
        }
        throw new SerdeException("Cannot deduct the subtype for bean " + deserBean.introspection.getBeanType().getName());
    }

    private static List<String> subtypePropertyKeys(DeserBeanSubtypeInfo<?> subtypeInfo) {
        Set<String> keys = new LinkedHashSet<>();
        for (DeserBeanSubtypeInfo.SubtypeDef<?> subtype : subtypeInfo.subtypes().values()) {
            DeserBean<?> subtypeDeserBean = subtype.deserBean();
            if (subtypeDeserBean == null) {
                continue;
            }
            addPropertyKeys(keys, subtypeDeserBean.injectProperties);
            addPropertyKeys(keys, subtypeDeserBean.creatorParams);
        }
        return new ArrayList<>(keys);
    }

    private static void addPropertyKeys(Set<String> keys, @Nullable PropertiesBag<?> properties) {
        if (properties != null) {
            keys.addAll(properties.getKeys());
        }
    }

}
