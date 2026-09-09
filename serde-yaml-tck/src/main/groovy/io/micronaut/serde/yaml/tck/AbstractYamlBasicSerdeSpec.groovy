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
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * Validates reading and writing of beans and records.
 */
abstract class AbstractYamlBasicSerdeSpec extends Specification implements YamlSpec {

    void "a record is written as a block mapping"() {
        expect:
        writeYaml(new RecordBean("The Stand", 454)) == "title: The Stand\npages: 454\n"
    }

    void "a record round trips"() {
        given:
        def bean = new RecordBean("The Stand", 454)

        expect:
        readYaml(writeYaml(bean), RecordBean) == bean
    }

    void "a mutable bean round trips"() {
        given:
        def bean = new SimpleBean("Hamza", 21)

        when:
        def read = readYaml(writeYaml(bean), SimpleBean)

        then:
        read.name == "Hamza"
        read.age == 21
    }

    void "a nested bean is written as a nested mapping"() {
        given:
        def bean = new ObjectBean("outer", new SimpleBean("Hamza", 21))

        when:
        def yaml = writeYaml(bean)
        def read = readYaml(yaml, ObjectBean)

        then:
        yaml.startsWith("name: outer\n")
        yaml.contains("inner:\n")
        read.name() == "outer"
        read.inner().name == "Hamza"
        read.inner().age == 21
    }

    void "a missing optional property is read as null"() {
        expect:
        readYaml("required: A\n", NullableBean) == new NullableBean("A", null)
    }

    void "an explicit null is read as null"() {
        expect:
        readYaml("required: A\noptional: " + nullForm + "\n", NullableBean) == new NullableBean("A", null)

        where:
        nullForm << ["null", "Null", "NULL", "~"]
    }

    void "an unknown property is ignored"() {
        expect:
        readYaml("title: T\nunknown: U\npages: 3\n", RecordBean) == new RecordBean("T", 3)
    }

    void "an unknown property holding a collection is ignored"() {
        expect:
        readYaml('''
title: T
unknown:
  nested:
    - 1
    - {a: b}
  other: [x, y]
pages: 3
trailing: [1, 2]
''', RecordBean) == new RecordBean("T", 3)
    }

    void "a bean is read from bytes and from a stream"() {
        given:
        def yaml = "title: T\npages: 3\n"

        expect:
        readYaml(yaml.getBytes(StandardCharsets.UTF_8), RecordBean) == new RecordBean("T", 3)
        readYaml(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), RecordBean) == new RecordBean("T", 3)
    }

    void "a bean is written to bytes and to a stream"() {
        given:
        def bean = new RecordBean("T", 3)
        def expected = "title: T\npages: 3\n"
        def output = new ByteArrayOutputStream()

        when:
        writeYaml(output, Argument.of(RecordBean), bean)

        then:
        new String(writeYamlAsBytes(bean), StandardCharsets.UTF_8) == expected
        new String(writeYamlAsBytes(Argument.of(RecordBean), bean), StandardCharsets.UTF_8) == expected
        writeYaml(Argument.of(RecordBean), bean) == expected
        new String(output.toByteArray(), StandardCharsets.UTF_8) == expected
    }
}
