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

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.yaml.YamlObjectMapper
import io.micronaut.serde.yaml.tck.YamlSpec

import java.nio.charset.StandardCharsets

/**
 * Runs the YAML TCK against the Micronaut Serialization YAML mapper.
 */
trait MicronautYamlSpec implements YamlSpec {

    abstract JsonMapper getYamlMapper()

    @Override
    def <T> T readYaml(String yaml, Argument<T> type) {
        readYaml(yaml.getBytes(StandardCharsets.UTF_8), type)
    }

    @Override
    def <T> T readYaml(byte[] yaml, Argument<T> type) {
        yamlMapper.readValue(yaml, type)
    }

    @Override
    def <T> T readYaml(InputStream yaml, Argument<T> type) {
        yamlMapper.readValue(yaml, type)
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
        yamlMapper.writeValueAsBytes(bean)
    }

    @Override
    byte[] writeYamlAsBytes(Argument<?> argument, Object bean) {
        yamlMapper.writeValueAsBytes(argument, bean)
    }

    @Override
    void writeYaml(OutputStream outputStream, Argument<?> argument, Object bean) {
        yamlMapper.writeValue(outputStream, argument, bean)
    }

    /**
     * Boots a dedicated {@link ApplicationContext} with the supplied Micronaut configuration
     * properties, resolves a configured {@link YamlObjectMapper} from it, runs the read, then
     * closes the context.
     */
    @Override
    def <T> T readYamlWithProperties(Map<String, Object> properties, String yaml, Argument<T> type) {
        withConfiguredMapper(properties) { YamlObjectMapper mapper ->
            mapper.readValue(yaml.getBytes(StandardCharsets.UTF_8), type)
        }
    }

    @Override
    String writeYamlWithProperties(Map<String, Object> properties, Object bean) {
        withConfiguredMapper(properties) { YamlObjectMapper mapper ->
            new String(mapper.writeValueAsBytes(bean), StandardCharsets.UTF_8)
        }
    }

    private <T> T withConfiguredMapper(Map<String, Object> properties, Closure<T> work) {
        ApplicationContext context = ApplicationContext.run(properties)
        try {
            return work.call(context.getBean(YamlObjectMapper))
        } finally {
            context.close()
        }
    }
}
