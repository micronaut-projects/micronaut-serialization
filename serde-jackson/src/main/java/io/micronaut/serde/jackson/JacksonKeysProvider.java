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
package io.micronaut.serde.jackson;

import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.KeyDescriptor;
import io.micronaut.serde.KeysProvider;
import tools.jackson.core.SerializableString;
import tools.jackson.core.io.SerializedString;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.sym.PropertyNameMatcher;
import tools.jackson.core.util.Named;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Contributes Jackson Core key data to {@link io.micronaut.serde.Keys}.
 *
 * @author Denis Stepanov
 * @since 3.1
 */
@Internal
public final class JacksonKeysProvider implements KeysProvider {

    static final int PROPERTY_NAME_MATCHER_INDEX = 0;
    static final int SERIALIZABLE_KEYS_INDEX = 1;

    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    @Override
    public Class<?> keysType() {
        return JacksonKeysProvider.class;
    }

    @Override
    public Object[] create(List<String> keys) {
        return create(keys, false);
    }

    @Override
    public Object[] create(List<String> keys, boolean caseInsensitive) {
        List<Named> names = new ArrayList<>(keys.size());
        SerializableString[] serializableKeys = new SerializableString[keys.size()];
        int index = 0;
        for (String key : keys) {
            names.add(Named.fromString(key));
            serializableKeys[index++] = new SerializedString(key);
        }
        return create(names, serializableKeys, caseInsensitive);
    }

    @Override
    public Object[] createWithMetadata(List<KeyDescriptor> keys, boolean caseInsensitive) {
        List<Named> names = new ArrayList<>(keys.size());
        SerializableString[] serializableKeys = new SerializableString[keys.size()];
        int index = 0;
        for (KeyDescriptor key : keys) {
            String name = key.name();
            names.add(Named.fromString(name));
            serializableKeys[index++] = new SerializedString(name);
        }
        return create(names, serializableKeys, caseInsensitive);
    }

    private Object[] create(List<Named> names,
                            SerializableString[] serializableKeys,
                            boolean caseInsensitive) {
        PropertyNameMatcher propertyNameMatcher = caseInsensitive
            ? JSON_FACTORY.constructCINameMatcher(names, false, Locale.ROOT)
            : JSON_FACTORY.constructNameMatcher(names, false);
        Object[] jacksonKeys = new Object[SERIALIZABLE_KEYS_INDEX + 1];
        jacksonKeys[PROPERTY_NAME_MATCHER_INDEX] = propertyNameMatcher;
        jacksonKeys[SERIALIZABLE_KEYS_INDEX] = serializableKeys;
        return jacksonKeys;
    }
}
