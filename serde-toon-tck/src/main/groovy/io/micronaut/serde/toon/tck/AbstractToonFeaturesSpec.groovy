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
import spock.lang.Specification

/**
 * Validates configurable TOON features like delimiters and indentations.
 */
abstract class AbstractToonFeaturesSpec extends Specification implements ToonSpec {

    private static final String DELIMITER_PROP = "micronaut.serde.format.toon.delimiter"
    private static final String INDENT_PROP = "micronaut.serde.format.toon.indent"

    void "custom delimiter is respected during encoding and decoding"() {
        given:
        def people = [
                new TabularPersonBean(1, "Alice", "Admin"),
                new TabularPersonBean(2, "Bob", "User")
        ]
        def properties = [(DELIMITER_PROP): delimiter]

        when:
        def encoded = writeToonWithProperties(properties, [users: people] as Map)
        def decoded = readToonWithProperties(properties, encoded, Argument.mapOf(Argument.of(String), Argument.listOf(TabularPersonBean)))

        then:
        decoded.users == people
        encoded.contains(headerMarker)

        where:
        delimiter | headerMarker
        ','       | '[2]{id,name,role}:'
        '\t'      | '[2\t]{id\tname\trole}:'
        '|'       | '[2|]{id|name|role}:'
    }

    void "custom indentation is respected during encoding"() {
        given:
        def bean = new ObjectBean("Outer", new SimpleBean("Inner", 20))
        def properties = [(INDENT_PROP): 4]

        when:
        def encoded = writeToonWithProperties(properties, bean)

        then:
        encoded.contains("    name: Inner")
        encoded.contains("    age: 20")
    }
}
