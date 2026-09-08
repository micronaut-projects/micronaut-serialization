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
package io.micronaut.serde.yaml.tck.jackson.databind

import io.micronaut.core.type.Argument
import io.micronaut.serde.yaml.tck.YamlSpec
import tools.jackson.databind.ObjectMapper
import tools.jackson.dataformat.yaml.YAMLAnchorReplayingFactory
import tools.jackson.dataformat.yaml.YAMLMapper

import java.nio.charset.StandardCharsets

/**
 * Runs the YAML TCK against Jackson Databind, so the Micronaut Serialization behaviour is
 * validated against the reference implementation.
 */
trait JacksonDatabindYamlSpec implements YamlSpec {

    YAMLMapper getDatabindYamlMapper() {
        JacksonYamlMappers.mapper([:])
    }

    @Override
    def <T> T readYaml(String yaml, Argument<T> type) {
        databindYamlMapper.readValue(yaml, JacksonYamlMappers.toJavaType(databindYamlMapper, type))
    }

    @Override
    def <T> T readYaml(byte[] yaml, Argument<T> type) {
        readYaml(new String(yaml, StandardCharsets.UTF_8), type)
    }

    @Override
    def <T> T readYaml(InputStream yaml, Argument<T> type) {
        readYaml(yaml.readAllBytes(), type)
    }

    /**
     * Jackson resolves aliases with a dedicated factory that replays anchored events.
     */
    @Override
    def <T> T readYamlWithAliases(String yaml, Argument<T> type) {
        def mapper = new ObjectMapper(new YAMLAnchorReplayingFactory())
        mapper.readValue(yaml, JacksonYamlMappers.toJavaType(mapper, type))
    }

    @Override
    def <T> T readYamlWithProperties(Map<String, Object> properties, String yaml, Argument<T> type) {
        def mapper = JacksonYamlMappers.mapper(properties)
        mapper.readValue(yaml, JacksonYamlMappers.toJavaType(mapper, type))
    }

    @Override
    String writeYamlWithProperties(Map<String, Object> properties, Object bean) {
        def mapper = JacksonYamlMappers.mapper(properties)
        JacksonYamlMappers.withExplicitEnd(properties, mapper.writeValueAsString(bean))
    }

    @Override
    String writeYaml(Object bean) {
        databindYamlMapper.writeValueAsString(bean)
    }

    @Override
    String writeYaml(Argument<?> argument, Object bean) {
        databindYamlMapper.writerFor(JacksonYamlMappers.toJavaType(databindYamlMapper, argument)).writeValueAsString(bean)
    }

    @Override
    byte[] writeYamlAsBytes(Object bean) {
        writeYaml(bean).getBytes(StandardCharsets.UTF_8)
    }

    @Override
    byte[] writeYamlAsBytes(Argument<?> argument, Object bean) {
        writeYaml(argument, bean).getBytes(StandardCharsets.UTF_8)
    }

    @Override
    void writeYaml(OutputStream outputStream, Argument<?> argument, Object bean) {
        outputStream.write(writeYamlAsBytes(argument, bean))
    }
}
