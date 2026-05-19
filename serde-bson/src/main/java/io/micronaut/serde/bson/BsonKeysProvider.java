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
package io.micronaut.serde.bson;

import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.KeysProvider;

import java.util.List;

/**
 * Contributes BSON key data to {@link io.micronaut.serde.Keys}.
 *
 * @author Denis Stepanov
 * @since 3.0
 */
@Internal
public final class BsonKeysProvider implements KeysProvider {

    static final int KEY_NAMES_INDEX = 0;

    @Override
    public Class<?> keysType() {
        return BsonKeysProvider.class;
    }

    @Override
    public Object[] create(List<String> keys) {
        return new Object[] { keys.toArray(String[]::new) };
    }
}
