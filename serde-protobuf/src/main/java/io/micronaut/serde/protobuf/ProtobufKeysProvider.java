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
package io.micronaut.serde.protobuf;

import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.KeysProvider;

import java.util.List;

/**
 * Contributes the property names of a {@link io.micronaut.serde.Keys} set to the protobuf backend.
 *
 * <p>Object properties are dispatched by index, but protobuf needs the field number the index
 * stands for, and that is only reachable through the property name. Contributing the names here
 * lets the encoder and decoder translate an index into a field number without a per-property
 * lookup on the hot path.</p>
 *
 * @since 3.2
 */
@Internal
public final class ProtobufKeysProvider implements KeysProvider {

    static final int KEY_NAMES_INDEX = 0;

    /**
     * Default constructor.
     */
    public ProtobufKeysProvider() {
    }

    @Override
    public Class<?> keysType() {
        return ProtobufKeysProvider.class;
    }

    @Override
    public Object[] create(List<String> keys) {
        return new Object[]{keys.toArray(String[]::new)};
    }
}
