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
package io.micronaut.serde.toon.tck.serde

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.toon.ToonMapper
import io.micronaut.serde.toon.tck.ToonSpec

import java.nio.charset.StandardCharsets

/**
 * Runs the TOON TCK against the Micronaut Serialization TOON mapper.
 */
trait MicronautToonSpec implements ToonSpec {

    abstract JsonMapper getToonMapper()

    @Override
    def <T> T readToon(String toon, Argument<T> type) {
        readToon(toon.getBytes(StandardCharsets.UTF_8), type)
    }

    @Override
    def <T> T readToon(byte[] toon, Argument<T> type) {
        toonMapper.readValue(toon, type)
    }

    @Override
    def <T> T readToon(InputStream toon, Argument<T> type) {
        toonMapper.readValue(toon, type)
    }

    @Override
    String writeToon(Object bean) {
        new String(writeToonAsBytes(bean), StandardCharsets.UTF_8)
    }

    @Override
    String writeToon(Argument<?> argument, Object bean) {
        new String(writeToonAsBytes(argument, bean), StandardCharsets.UTF_8)
    }

    @Override
    byte[] writeToonAsBytes(Object bean) {
        toonMapper.writeValueAsBytes(bean)
    }

    @Override
    byte[] writeToonAsBytes(Argument<?> argument, Object bean) {
        toonMapper.writeValueAsBytes(argument, bean)
    }

    @Override
    void writeToon(OutputStream outputStream, Argument<?> argument, Object bean) {
        toonMapper.writeValue(outputStream, argument, bean)
    }

    @Override
    def <T> T readToonWithProperties(Map<String, Object> properties, String toon, Argument<T> type) {
        withConfiguredMapper(properties) { ToonMapper mapper ->
            mapper.readValue(toon.getBytes(StandardCharsets.UTF_8), type)
        }
    }

    @Override
    String writeToonWithProperties(Map<String, Object> properties, Object bean) {
        withConfiguredMapper(properties) { ToonMapper mapper ->
            new String(mapper.writeValueAsBytes(bean), StandardCharsets.UTF_8)
        }
    }

    private <T> T withConfiguredMapper(Map<String, Object> properties, Closure<T> work) {
        ApplicationContext context = ApplicationContext.run(properties)
        try {
            return work.call(context.getBean(ToonMapper))
        } finally {
            context.close()
        }
    }
}
