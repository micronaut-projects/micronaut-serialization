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
package io.micronaut.serde.yaml;

import io.micronaut.core.annotation.Internal;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.schema.CoreSchema;

/**
 * The parser settings a {@link YamlDecoder} reads with, derived once from a
 * {@link SerdeYamlConfiguration} so that decoders do not rebuild them per document.
 *
 * @param loadSettings The low level parser settings
 * @param booleanAsStrings Whether legacy YAML 1.1 boolean words are read as strings
 * @param emptyStringAsNull Whether an empty plain scalar is read as {@code null}
 * @since 3.2.0
 */
@Internal
record YamlReadSettings(LoadSettings loadSettings, boolean booleanAsStrings, boolean emptyStringAsNull) {

    /**
     * The settings of a default {@link SerdeYamlConfiguration}.
     */
    static final YamlReadSettings DEFAULT = from(new SerdeYamlConfiguration());

    /**
     * Derives the read settings from the configuration.
     *
     * @param configuration The YAML configuration
     * @return The read settings
     */
    static YamlReadSettings from(SerdeYamlConfiguration configuration) {
        LoadSettings loadSettings = LoadSettings.builder()
            .setSchema(new CoreSchema())
            .setCodePointLimit(configuration.getCodePointLimit())
            .build();
        return new YamlReadSettings(loadSettings, configuration.isBooleanAsStrings(), configuration.isEmptyStringAsNull());
    }
}
