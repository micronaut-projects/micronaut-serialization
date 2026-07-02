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
package io.micronaut.serde.yaml

import io.micronaut.core.type.Argument

import java.nio.charset.StandardCharsets

abstract class AbstractMicronautYamlSpec extends AbstractYamlSerializationSpec {

    @Override
    def <T> T readYaml(String properties, Argument<T> type) {
        readYaml(properties.getBytes(StandardCharsets.UTF_8), type)
    }

    @Override
    def <T> T readYaml(byte[] properties, Argument<T> type) {
        jsonMapper.readValue(properties, type)
    }

    @Override
    def <T> T readYaml(InputStream properties, Argument<T> type) {
        jsonMapper.readValue(properties, type)
    }

    @Override
    String writeYaml(Object bean) {
        new String(writeYamlAsBytes(bean), StandardCharsets.UTF_8)
    }

    @Override
    String writeYaml(Argument<?> argument, Object bean) {
        new String(writeYamlAsBytes(argument, bean), StandardCharsets.UTF_8)
    }

    @Override
    byte[] writeYamlAsBytes(Object bean) {
        jsonMapper.writeValueAsBytes(bean)
    }

    @Override
    byte[] writeYamlAsBytes(Argument<?> argument, Object bean) {
        jsonMapper.writeValueAsBytes(argument, bean)
    }

    @Override
    void writeYaml(OutputStream outputStream, Argument<?> argument, Object bean) {
        jsonMapper.writeValue(outputStream, argument, bean)
    }

}
