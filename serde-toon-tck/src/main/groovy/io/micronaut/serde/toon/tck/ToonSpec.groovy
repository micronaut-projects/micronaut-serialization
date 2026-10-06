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
package io.micronaut.serde.toon.tck

import io.micronaut.core.type.Argument

/**
 * The service provider interface of the TOON TCK.
 *
 * <p>A runner implements the read and write operations with the mapper under test. Every
 * abstract specification of the TCK is written against this interface only, so the same tests
 * validate any TOON implementation.</p>
 */
trait ToonSpec {

    abstract <T> T readToon(String toon, Argument<T> type)

    abstract <T> T readToon(byte[] toon, Argument<T> type)

    abstract <T> T readToon(InputStream toon, Argument<T> type)

    abstract String writeToon(Object bean)

    abstract String writeToon(Argument<?> argument, Object bean)

    abstract byte[] writeToonAsBytes(Object bean)

    abstract byte[] writeToonAsBytes(Argument<?> argument, Object bean)

    abstract void writeToon(OutputStream outputStream, Argument<?> argument, Object bean)

    /**
     * Reads TOON with a mapper configured by the given Micronaut-style properties.
     *
     * <p>Keys are canonical Micronaut configuration keys, for example
     * {@code micronaut.serde.format.toon.delimiter}. Each runner
     * adapts them to the configuration model of the mapper under test.</p>
     */
    abstract <T> T readToonWithProperties(Map<String, Object> properties, String toon, Argument<T> type)

    /**
     * Writes TOON with a mapper configured by the given Micronaut-style properties.
     *
     * <p>Keys are canonical Micronaut configuration keys, for example
     * {@code micronaut.serde.format.toon.delimiter} or {@code micronaut.serde.format.toon.indent}.
     * Each runner adapts them to the configuration model of the mapper under test.</p>
     */
    abstract String writeToonWithProperties(Map<String, Object> properties, Object bean)

    def <T> T readToon(String toon, Class<T> type) {
        readToon(toon, Argument.of(type))
    }

    def <T> T readToon(byte[] toon, Class<T> type) {
        readToon(toon, Argument.of(type))
    }

    def <T> T readToon(InputStream toon, Class<T> type) {
        readToon(toon, Argument.of(type))
    }

    def <T> T readToonWithProperties(Map<String, Object> properties, String toon, Class<T> type) {
        readToonWithProperties(properties, toon, Argument.of(type))
    }
}
