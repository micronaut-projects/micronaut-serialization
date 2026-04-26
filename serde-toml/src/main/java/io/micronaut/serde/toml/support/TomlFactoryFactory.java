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
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.config.SerdeConfiguration;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamWriteConstraints;
import tools.jackson.dataformat.toml.TomlFactory;
import tools.jackson.dataformat.toml.TomlFactoryBuilder;
import tools.jackson.dataformat.toml.TomlReadFeature;
import tools.jackson.dataformat.toml.TomlWriteFeature;

/**
 * Builds TOML factories aligned with Micronaut serde limits.
 */
@Internal
public final class TomlFactoryFactory {

    private TomlFactoryFactory() {
    }

    public static TomlFactory create(@Nullable SerdeConfiguration serdeConfiguration) {
        return create(serdeConfiguration, null);
    }

    public static TomlFactory create(@Nullable SerdeConfiguration serdeConfiguration,
                                     @Nullable SerdeTomlConfiguration tomlConfiguration) {
        int maximumNestingDepth = serdeConfiguration == null
            ? LimitingStream.DEFAULT_MAXIMUM_DEPTH
            : serdeConfiguration.getMaximumNestingDepth();

        StreamReadConstraints.Builder readConstraints = StreamReadConstraints.builder()
            .maxNestingDepth(maximumNestingDepth);
        if (tomlConfiguration != null) {
            if (tomlConfiguration.getMaxNumberLength() != null) {
                readConstraints.maxNumberLength(tomlConfiguration.getMaxNumberLength());
            }
            if (tomlConfiguration.getMaxStringLength() != null) {
                readConstraints.maxStringLength(tomlConfiguration.getMaxStringLength());
            }
        }

        return create(
            readConstraints.build(),
            StreamWriteConstraints.builder()
                .maxNestingDepth(maximumNestingDepth)
                .build(),
            tomlConfiguration
        );
    }

    public static TomlFactory create(StreamReadConstraints readConstraints,
                                     StreamWriteConstraints writeConstraints) {
        return create(readConstraints, writeConstraints, null);
    }

    public static TomlFactory create(StreamReadConstraints readConstraints,
                                     StreamWriteConstraints writeConstraints,
                                     @Nullable SerdeTomlConfiguration tomlConfiguration) {
        TomlFactoryBuilder builder = TomlFactory.builder()
            .streamReadConstraints(readConstraints)
            .streamWriteConstraints(writeConstraints);
        if (tomlConfiguration != null) {
            builder = builder.configure(TomlReadFeature.PARSE_JAVA_TIME, tomlConfiguration.isParseJavaTime());
            builder = builder.configure(TomlWriteFeature.FAIL_ON_NULL_WRITE, tomlConfiguration.isFailOnNullWrite());
        }
        return builder.build();
    }
}
