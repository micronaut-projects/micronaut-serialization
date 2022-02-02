/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.serde.support.config;

import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.Internal;
import io.micronaut.json.JsonConfiguration;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.SerializationConfiguration;
import jakarta.inject.Singleton;

/**
 * Implementation of the {@link io.micronaut.json.JsonConfiguration} interface
 * for the serialization project.
 */
@Internal
@Singleton
@Secondary
public class SerdeJsonConfiguration implements JsonConfiguration {
    private final SerializationConfiguration serialization;
    private final DeserializationConfiguration deserialization;

    public SerdeJsonConfiguration(SerializationConfiguration serialization,
                                  DeserializationConfiguration deserialization) {
        this.serialization = serialization;
        this.deserialization = deserialization;
    }

    @Override
    public boolean isAlwaysSerializeErrorsAsList() {
        return serialization.isAlwaysSerializeErrorsAsList();
    }

    @Override
    public int getArraySizeThreshold() {
        return deserialization.getArraySizeThreshold();
    }
}
