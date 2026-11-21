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
package io.micronaut.serde.support;

import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;

/**
 * The document serdes of the default contexts, created when the first one is stored.
 */
final class DocumentSerdeMaps {

    private DocumentSerdeMaps() {
    }

    static @Nullable Object get(@Nullable IdentityHashMap<Object, Object> serdes, Object owner) {
        return serdes == null ? null : serdes.get(owner);
    }

    static IdentityHashMap<Object, Object> put(@Nullable IdentityHashMap<Object, Object> serdes, Object owner, Object value) {
        IdentityHashMap<Object, Object> map = serdes == null ? new IdentityHashMap<>(4) : serdes;
        map.put(owner, value);
        return map;
    }
}
