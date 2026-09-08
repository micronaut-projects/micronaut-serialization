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
 * Validates anchors, aliases and merge keys.
 */
abstract class AbstractYamlAnchorAliasSpec extends Specification implements YamlSpec {

    void "a scalar anchor is replayed by its alias"() {
        when:
        def read = readYamlWithAliases('''
a: &x A
b: *x
''', Argument.mapOf(String, String))

        then:
        read.a == "A"
        read.b == "A"
    }

    void "a sequence anchor is replayed by its alias"() {
        when:
        def read = readYamlWithAliases('''
first: &list
  - 1
  - 2
  - 3
second: *list
''', Argument.mapOf(Argument.STRING, Argument.listOf(Integer)))

        then:
        read.first == [1, 2, 3]
        read.second == [1, 2, 3]
    }

    void "a mapping anchor is replayed by its alias"() {
        when:
        def read = readYamlWithAliases('''
inner: &anchor
  name: Hamza
  age: 21
copy: *anchor
''', Argument.mapOf(String, SimpleBean))

        then:
        read.inner.name == "Hamza"
        read.copy.name == "Hamza"
        read.copy.age == 21
    }

    void "an anchored node containing collections is replayed"() {
        when:
        def read = readYamlWithAliases('''
first: &anchor
  values:
    - one
    - two
  counts:
    a: 1
second: *anchor
''', Argument.mapOf(String, CollectionsBean))

        then:
        read.first.values() == ["one", "two"]
        read.second.values() == ["one", "two"]
        read.second.counts() == [a: 1]
    }

    void "the yaml specification example of a repeated scalar node is read"() {
        when:
        def read = readYamlWithAliases('''
---
hr:
  - Mark McGwire
  # Following node labeled SS
  - &SS Sammy Sosa
rbi:
  - *SS # Subsequent occurrence
  - Ken Griffey
''', Argument.mapOf(Argument.STRING, Argument.listOf(String)))

        then:
        read.hr == ["Mark McGwire", "Sammy Sosa"]
        read.rbi == ["Sammy Sosa", "Ken Griffey"]
    }

    void "a merge key splices the merged mapping"() {
        when:
        def read = readYamlWithAliases('''
base: &base
  name: Hamza
  age: 21
derived:
  <<: *base
  age: 30
''', Argument.mapOf(String, SimpleBean))

        then:
        read.derived.name == "Hamza"
        read.derived.age == 30
        read.base.age == 21
    }

    void "an anchor on a merged value can be aliased"() {
        when:
        def read = readYamlWithAliases('''
derived:
  <<: &base {name: Hamza}
  age: 30
other: *base
''', Argument.mapOf(String, SimpleBean))

        then:
        read.derived.name == "Hamza"
        read.derived.age == 30
        read.other.name == "Hamza"
    }

    void "a merge key accepts an inline mapping"() {
        when:
        def read = readYamlWithAliases('''
derived:
  <<: {name: Hamza}
  age: 21
''', Argument.mapOf(String, SimpleBean))

        then:
        read.derived.name == "Hamza"
        read.derived.age == 21
    }

    void "an alias without a matching anchor is rejected"() {
        when:
        readYamlWithAliases("a: *missing\n", Argument.mapOf(String, String))

        then:
        thrown(Exception)
    }
}
