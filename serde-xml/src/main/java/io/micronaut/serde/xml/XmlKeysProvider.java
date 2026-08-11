/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.serde.xml;

import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.KeyDescriptor;
import io.micronaut.serde.KeysProvider;
import io.micronaut.serde.config.annotation.SerdeConfig;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Precomputes XML property layout from reusable key descriptors.
 *
 * @since 3.2
 */
@Internal
public final class XmlKeysProvider implements KeysProvider {

    static final int XML_KEYS_INDEX = 0;
    static final int INPUT_NAME_INDEXES_INDEX = 1;

    @Override
    public Class<?> keysType() {
        return XmlKeysProvider.class;
    }

    @Override
    public Object[] create(List<String> keys) {
        return create(keys, false);
    }

    @Override
    public Object[] create(List<String> keys, boolean caseInsensitive) {
        return createWithMetadata(keys.stream().map(KeyDescriptor::new).toList(), caseInsensitive);
    }

    @Override
    public Object[] createWithMetadata(List<KeyDescriptor> keys, boolean caseInsensitive) {
        XmlKey[] xmlKeys = new XmlKey[keys.size()];
        Map<String, Integer> inputNameIndexes = new HashMap<>(keys.size());
        for (int i = 0; i < keys.size(); i++) {
            KeyDescriptor key = keys.get(i);
            Map<String, String> metadata = key.metadata();
            @Nullable String wrapping = metadata.get(SerdeConfig.META_ANNOTATION_PROPERTY);
            XmlCollectionLayout collectionLayout = wrapping == null
                ? XmlCollectionLayout.DEFAULT
                : Boolean.parseBoolean(wrapping) ? XmlCollectionLayout.WRAPPED : XmlCollectionLayout.INLINE;
            @Nullable String wrapperName = metadata.get(SerdeConfig.WRAPPER_PROPERTY);
            XmlKey xmlKey = new XmlKey(
                key.name(),
                metadata.get(SerdeConfig.XML_NAMESPACE),
                Boolean.parseBoolean(metadata.get(SerdeConfig.XML_ATTRIBUTE_PROPERTY)),
                collectionLayout,
                wrapperName
            );
            xmlKeys[i] = xmlKey;
            inputNameIndexes.putIfAbsent(normalize(key.name(), caseInsensitive), i);
            if (collectionLayout == XmlCollectionLayout.WRAPPED && wrapperName != null) {
                inputNameIndexes.putIfAbsent(normalize(wrapperName, caseInsensitive), i);
            }
        }
        return new Object[] { xmlKeys, Map.copyOf(inputNameIndexes) };
    }

    static String normalize(String name, boolean caseInsensitive) {
        return caseInsensitive ? name.toLowerCase(Locale.ROOT) : name;
    }
}

record XmlKey(
    String name,
    @Nullable String namespace,
    boolean attribute,
    XmlCollectionLayout collectionLayout,
    @Nullable String wrapperName
) {
}

enum XmlCollectionLayout {
    DEFAULT,
    WRAPPED,
    INLINE
}
