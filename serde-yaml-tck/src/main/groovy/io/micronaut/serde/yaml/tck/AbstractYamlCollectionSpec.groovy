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

/**
 * Validates reading and writing of YAML sequences and mappings.
 */
abstract class AbstractYamlCollectionSpec extends Specification implements YamlSpec {

    void "collections are written as block sequences and mappings"() {
        given:
        def bean = new CollectionsBean(["A", "B"], [one: 1, two: 2], null)

        expect:
        writeYaml(bean) == "values:\n- A\n- B\ncounts:\n  one: 1\n  two: 2\nbeans: null\n"
    }

    void "collections round trip"() {
        given:
        def bean = new CollectionsBean(["A", "B"], [one: 1, two: 2], [new SimpleBean("Hamza", 21)])

        when:
        def read = readYaml(writeYaml(bean), CollectionsBean)

        then:
        read.values() == ["A", "B"]
        read.counts() == [one: 1, two: 2]
        read.beans().size() == 1
        read.beans()[0].name == "Hamza"
    }

    void "a block sequence is read"() {
        expect:
        readYaml("values:\n  - A\n  - B\n  - C\n", CollectionsBean).values() == ["A", "B", "C"]
    }

    void "a flow sequence is read"() {
        expect:
        readYaml("values: [A, B, C]\n", CollectionsBean).values() == ["A", "B", "C"]
    }

    void "a flow mapping is read"() {
        expect:
        readYaml("counts: {one: 1, two: 2}\n", CollectionsBean).counts() == [one: 1, two: 2]
    }

    void "an empty flow collection is read"() {
        when:
        def read = readYaml("values: []\ncounts: {}\n", CollectionsBean)

        then:
        read.values() == []
        read.counts() == [:]
    }

    void "a sequence of mappings is read"() {
        when:
        def read = readYaml("beans:\n  - name: A\n    age: 1\n  - name: B\n    age: 2\n", CollectionsBean)

        then:
        read.beans().size() == 2
        read.beans()[0].name == "A"
        read.beans()[1].age == 2
    }

    void "a root sequence is read"() {
        expect:
        readYaml("- A\n- B\n", Argument.listOf(String)) == ["A", "B"]
        readYaml("- title: One\n  pages: 1\n- title: Two\n  pages: 2\n", Argument.listOf(RecordBean)) ==
                [new RecordBean("One", 1), new RecordBean("Two", 2)]
    }

    void "a root mapping is read as a map"() {
        expect:
        readYaml("a: 1\nb: 2\n", Argument.mapOf(String, Integer)) == [a: 1, b: 2]
    }

    void "a root sequence round trips"() {
        given:
        def beans = [new RecordBean("One", 1), new RecordBean("Two", 2)]

        expect:
        writeYaml(Argument.listOf(RecordBean), beans) == "- title: One\n  pages: 1\n- title: Two\n  pages: 2\n"
        readYaml(writeYaml(Argument.listOf(RecordBean), beans), Argument.listOf(RecordBean)) == beans
    }
}
