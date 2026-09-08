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
import io.micronaut.serde.yaml.YamlObjectMapper
import spock.lang.Specification

/**
 * Read features that have no Jackson counterpart, so they are validated for Micronaut
 * Serialization only.
 */
class SerdeYamlReadFeaturesSpec extends Specification {

    void "legacy YAML boolean words are read as booleans when configured"() {
        given:
        def context = ApplicationContext.run(
                ['micronaut.serde.format.yaml.read-features.boolean-as-strings': false]
        )
        def mapper = context.getBean(YamlObjectMapper)

        expect:
        mapper.readValue('key: ' + value + '\n', Argument.mapOf(String, Object)).key == expected

        cleanup:
        context.close()

        where:
        value || expected
        "yes" || true
        "Yes" || true
        "YES" || true
        "no"  || false
        "No"  || false
        "NO"  || false
        "y"   || "y"
        "Y"   || "Y"
        "n"   || "n"
        "N"   || "N"
        "on"  || true
        "On"  || true
        "ON"  || true
        "off" || false
        "Off" || false
        "OFF" || false
    }

    void "the parser code point limit is configurable"() {
        given:
        def context = ApplicationContext.run(
                ['micronaut.serde.format.yaml.read-features.code-point-limit': 20]
        )
        def mapper = context.getBean(YamlObjectMapper)

        when:
        mapper.readValue('key: ' + ('x' * 64) + '\n', Argument.mapOf(String, Object))

        then:
        thrown(Exception)

        when:
        def read = mapper.readValue('key: value\n', Argument.mapOf(String, Object))

        then:
        read.key == 'value'

        cleanup:
        context.close()
    }

    /**
     * Jackson Databind writes a numeric looking string unquoted while quotes are minimized, so it
     * reads the value back as a number. Micronaut Serialization quotes it, which is asserted here
     * rather than in the shared TCK.
     */
    void "a string that looks like a number round trips as a string"() {
        given:
        def context = ApplicationContext.run()
        def mapper = context.getBean(YamlObjectMapper)

        when:
        def yaml = new String(mapper.writeValueAsBytes([a: value]))

        then:
        mapper.readValue(yaml, Argument.mapOf(String, Object)).a == value

        cleanup:
        context.close()

        where:
        value << ["42", "1.5", "001", "0x1F", ".inf"]
    }
}
