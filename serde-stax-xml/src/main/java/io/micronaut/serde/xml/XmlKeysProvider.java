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
import io.micronaut.serde.Keys;
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
    static final int TEXT_KEY_INDEX = 2;

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
        XmlKey[] xmlKeys = new XmlKey[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            xmlKeys[i] = new XmlKey(
                keys.get(i),
                null,
                false,
                false,
                false,
                XmlCollectionLayout.DEFAULT,
                null,
                null
            );
        }
        return new Object[] { xmlKeys, Map.of(), Keys.UNKNOWN_KEY };
    }

    @Override
    public Object[] createWithMetadata(List<KeyDescriptor> keys, boolean caseInsensitive) {
        XmlKey[] xmlKeys = new XmlKey[keys.size()];
        @Nullable Map<String, Integer> inputNameIndexes = null;
        int textKeyIndex = Keys.UNKNOWN_KEY;
        for (int i = 0; i < keys.size(); i++) {
            KeyDescriptor key = keys.get(i);
            XmlKey xmlKey = createXmlKey(key);
            xmlKeys[i] = xmlKey;
            if (xmlKey.text() && textKeyIndex == Keys.UNKNOWN_KEY) {
                textKeyIndex = i;
            }
            inputNameIndexes = addWrapperNameIndex(inputNameIndexes, xmlKey, caseInsensitive, i);
        }
        return new Object[] {
            xmlKeys,
            inputNameIndexes == null ? Map.of() : Map.copyOf(inputNameIndexes),
            textKeyIndex
        };
    }

    private static XmlKey createXmlKey(KeyDescriptor key) {
        Map<String, String> metadata = key.metadata();
        return new XmlKey(
            key.name(),
            metadata.get(SerdeConfig.XML_NAMESPACE),
            Boolean.parseBoolean(metadata.get(SerdeConfig.XML_ATTRIBUTE_PROPERTY)),
            Boolean.parseBoolean(metadata.get(SerdeConfig.XML_TEXT_PROPERTY)),
            Boolean.parseBoolean(metadata.get(SerdeConfig.XML_CDATA_PROPERTY)),
            collectionLayout(metadata.get(SerdeConfig.META_ANNOTATION_PROPERTY)),
            metadata.get(SerdeConfig.WRAPPER_PROPERTY),
            metadata.get(SerdeConfig.XML_WRAPPER_NAMESPACE)
        );
    }

    private static XmlCollectionLayout collectionLayout(@Nullable String wrapping) {
        if (wrapping == null) {
            return XmlCollectionLayout.DEFAULT;
        }
        return Boolean.parseBoolean(wrapping) ? XmlCollectionLayout.WRAPPED : XmlCollectionLayout.INLINE;
    }

    private static @Nullable Map<String, Integer> addWrapperNameIndex(
        @Nullable Map<String, Integer> inputNameIndexes,
        XmlKey xmlKey,
        boolean caseInsensitive,
        int index
    ) {
        String wrapperName = xmlKey.wrapperName();
        if (xmlKey.collectionLayout() != XmlCollectionLayout.WRAPPED || wrapperName == null) {
            return inputNameIndexes;
        }
        Map<String, Integer> indexes = inputNameIndexes == null ? new HashMap<>() : inputNameIndexes;
        indexes.putIfAbsent(normalize(wrapperName, caseInsensitive), index);
        return indexes;
    }

    static String normalize(String name, boolean caseInsensitive) {
        return caseInsensitive ? name.toLowerCase(Locale.ROOT) : name;
    }
}

record XmlKey(
    String name,
    @Nullable String namespace,
    boolean attribute,
    boolean text,
    boolean cdata,
    XmlCollectionLayout collectionLayout,
    @Nullable String wrapperName,
    @Nullable String wrapperNamespace
) {
}

enum XmlCollectionLayout {
    DEFAULT,
    WRAPPED,
    INLINE
}
