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
package io.micronaut.serde.toml.support;

import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.config.SerializationConfiguration;
import io.micronaut.serde.config.annotation.SerdeConfig;

/**
 * TOML-local serialization configuration overrides.
 */
@Internal
public final class TomlSerializationConfiguration implements SerializationConfiguration {
    private final SerializationConfiguration delegate;

    public TomlSerializationConfiguration(SerializationConfiguration delegate) {
        this.delegate = delegate;
    }

    @Override
    public SerdeConfig.SerInclude getInclusion() {
        return SerdeConfig.SerInclude.ALWAYS;
    }

    @Override
    public boolean isAlwaysSerializeErrorsAsList() {
        return delegate.isAlwaysSerializeErrorsAsList();
    }

    @Override
    public boolean sortPropertiesAlphabetically() {
        return delegate.sortPropertiesAlphabetically();
    }
}
