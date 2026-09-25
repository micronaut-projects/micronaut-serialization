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
package io.micronaut.serde.toon.tck.jtoon

import dev.toonformat.jtoon.DecodeOptions
import dev.toonformat.jtoon.EncodeOptions
import dev.toonformat.jtoon.JToon
import io.micronaut.core.type.Argument
import io.micronaut.serde.toon.tck.ToonSpec
import tools.jackson.databind.ObjectMapper

import java.nio.charset.StandardCharsets

/**
 * Runs the TOON TCK against the reference dev.toonformat:jtoon implementation.
 */
trait JToonSpec implements ToonSpec {

    private ObjectMapper jacksonObjectMapper() {
        new ObjectMapper()
    }

    @Override
    def <T> T readToon(String toon, Argument<T> type) {
        String json = JToon.decodeToJson(toon)
        jacksonObjectMapper().readValue(json, JToonMappers.toJavaType(jacksonObjectMapper(), type))
    }

    @Override
    def <T> T readToon(byte[] toon, Argument<T> type) {
        readToon(new String(toon, StandardCharsets.UTF_8), type)
    }

    @Override
    def <T> T readToon(InputStream toon, Argument<T> type) {
        readToon(new String(toon.readAllBytes(), StandardCharsets.UTF_8), type)
    }

    @Override
    String writeToon(Object bean) {
        def mapper = jacksonObjectMapper()
        Object jsonFriendly = mapper.convertValue(bean, Object)
        JToon.encode(jsonFriendly)
    }

    @Override
    String writeToon(Argument<?> argument, Object bean) {
        writeToon(bean)
    }

    @Override
    byte[] writeToonAsBytes(Object bean) {
        writeToon(bean).getBytes(StandardCharsets.UTF_8)
    }

    @Override
    byte[] writeToonAsBytes(Argument<?> argument, Object bean) {
        writeToon(argument, bean).getBytes(StandardCharsets.UTF_8)
    }

    @Override
    void writeToon(OutputStream outputStream, Argument<?> argument, Object bean) {
        outputStream.write(writeToonAsBytes(argument, bean))
    }

    @Override
    def <T> T readToonWithProperties(Map<String, Object> properties, String toon, Argument<T> type) {
        DecodeOptions options = JToonMappers.decodeOptions(properties)
        String json = JToon.decodeToJson(toon, options)
        jacksonObjectMapper().readValue(json, JToonMappers.toJavaType(jacksonObjectMapper(), type))
    }

    @Override
    String writeToonWithProperties(Map<String, Object> properties, Object bean) {
        EncodeOptions options = JToonMappers.encodeOptions(properties)
        Object jsonFriendly = jacksonObjectMapper().convertValue(bean, Object)
        JToon.encode(jsonFriendly, options)
    }
}
