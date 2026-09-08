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
package io.micronaut.serde.yaml.tck

import io.micronaut.core.type.Argument

/**
 * The service provider interface of the YAML TCK.
 *
 * <p>A runner implements the read and write operations with the mapper under test. Every
 * abstract specification of the TCK is written against this interface only, so the same tests
 * validate any YAML implementation.</p>
 */
trait YamlSpec {

    abstract <T> T readYaml(String yaml, Argument<T> type)

    abstract <T> T readYaml(byte[] yaml, Argument<T> type)

    abstract <T> T readYaml(InputStream yaml, Argument<T> type)

    abstract String writeYaml(Object bean)

    abstract String writeYaml(Argument<?> argument, Object bean)

    abstract byte[] writeYamlAsBytes(Object bean)

    abstract byte[] writeYamlAsBytes(Argument<?> argument, Object bean)

    abstract void writeYaml(OutputStream outputStream, Argument<?> argument, Object bean)

    /**
     * Reads YAML with a mapper configured by the given Micronaut-style properties.
     *
     * <p>Keys are canonical Micronaut configuration keys, for example
     * {@code micronaut.serde.format.yaml.read-features.empty-string-as-null}. Each runner
     * adapts them to the configuration model of the mapper under test.</p>
     */
    abstract <T> T readYamlWithProperties(Map<String, Object> properties, String yaml, Argument<T> type)

    /**
     * Writes YAML with a mapper configured by the given Micronaut-style properties.
     *
     * <p>Keys are canonical Micronaut configuration keys, for example
     * {@code micronaut.serde.format.yaml.write-features.minimize-quotes}. Each runner adapts
     * them to the configuration model of the mapper under test.</p>
     */
    abstract String writeYamlWithProperties(Map<String, Object> properties, Object bean)

    def <T> T readYaml(String yaml, Class<T> type) {
        readYaml(yaml, Argument.of(type))
    }

    def <T> T readYaml(byte[] yaml, Class<T> type) {
        readYaml(yaml, Argument.of(type))
    }

    def <T> T readYaml(InputStream yaml, Class<T> type) {
        readYaml(yaml, Argument.of(type))
    }

    def <T> T readYamlWithProperties(Map<String, Object> properties, String yaml, Class<T> type) {
        readYamlWithProperties(properties, yaml, Argument.of(type))
    }

    /**
     * Reads YAML that uses anchors, aliases or merge keys.
     *
     * <p>Defaults to {@link #readYaml}. A runner whose mapper needs a dedicated parser to
     * resolve aliases overrides this.</p>
     */
    def <T> T readYamlWithAliases(String yaml, Argument<T> type) {
        readYaml(yaml, type)
    }

    def <T> T readYamlWithAliases(String yaml, Class<T> type) {
        readYamlWithAliases(yaml, Argument.of(type))
    }
}
