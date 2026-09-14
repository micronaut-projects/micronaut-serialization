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
package io.micronaut.serde.yaml.tck.serde

import io.micronaut.json.JsonMapper
import io.micronaut.serde.yaml.YamlObjectMapper
import io.micronaut.context.annotation.Property
import io.micronaut.serde.yaml.tck.AbstractYamlCollectionSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

/**
 * The shared expectations write a null property explicitly, which Jackson Databind does by default and
 * Micronaut Serialization does under the {@code ALWAYS} inclusion; the default {@code NON_EMPTY} omits it.
 */
@Property(name = "micronaut.serde.serialization.inclusion", value = "ALWAYS")
@MicronautTest
class SerdeYamlCollectionSpec extends AbstractYamlCollectionSpec implements MicronautYamlSpec {

    @Inject
    YamlObjectMapper mapper

    @Override
    JsonMapper getYamlMapper() {
        mapper
    }
}
