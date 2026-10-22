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
 * Validates collection, list, map, and tabular array handling across TOON implementations.
 */
abstract class AbstractToonCollectionSpec extends Specification implements ToonSpec {

    void "a list of scalars is serialized and deserialized"() {
        given:
        def list = ["one", "two", "three"]

        when:
        def encoded = writeToon([items: list] as Map)
        def decoded = readToon(encoded, Argument.mapOf(Argument.of(String), Argument.listOf(String)))

        then:
        encoded.trim() == "items[3]: one,two,three"
        decoded.items == list
    }

    void "a list of tabular objects round trips"() {
        given:
        def people = [
                new TabularPersonBean(1, "Alice", "Admin"),
                new TabularPersonBean(2, "Bob", "User")
        ]

        when:
        def encoded = writeToon([users: people] as Map)
        def decoded = readToon(encoded, Argument.mapOf(Argument.of(String), Argument.listOf(TabularPersonBean)))

        then:
        encoded.trim() == "users[2]{id,name,role}:\n  1,Alice,Admin\n  2,Bob,User"
        decoded.users == people
    }

    void "a map of numbers round trips"() {
        given:
        def counts = [apples: 5, bananas: 12, oranges: 7]

        when:
        def encoded = writeToon([inventory: counts] as Map)
        def decoded = readToon(encoded, Argument.mapOf(Argument.of(String), Argument.mapOf(String, Integer)))

        then:
        decoded.inventory == counts
    }

    void "a CollectionsBean with lists, maps, and objects round trips"() {
        given:
        def bean = new CollectionsBean(
                ["alpha", "beta"],
                [first: 1, second: 2],
                [new SimpleBean("Charlie", 40), new SimpleBean("Dana", 22)]
        )

        when:
        def encoded = writeToon(bean)
        def decoded = readToon(encoded, CollectionsBean)

        then:
        encoded.trim() == "values[2]: alpha,beta\ncounts:\n  first: 1\n  second: 2\nbeans[2]{name,age}:\n  Charlie,40\n  Dana,22"
        decoded.values() == ["alpha", "beta"]
        decoded.counts() == [first: 1, second: 2]
        decoded.beans().size() == 2
        decoded.beans()[0].name == "Charlie"
        decoded.beans()[0].age == 40
        decoded.beans()[1].name == "Dana"
        decoded.beans()[1].age == 22
    }

    void "a root sequence is read"() {
        expect:
        readToon("[2]: A,B\n", Argument.listOf(String)) == ["A", "B"]
        readToon("[2]{title,pages}:\n  One,1\n  Two,2\n", Argument.listOf(RecordBean)) ==
                [new RecordBean("One", 1), new RecordBean("Two", 2)]
    }

    void "a root sequence round trips"() {
        given:
        def beans = [new RecordBean("One", 1), new RecordBean("Two", 2)]

        expect:
        writeToon(Argument.listOf(RecordBean), beans).trim() == "[2]{title,pages}:\n  One,1\n  Two,2"
        readToon(writeToon(Argument.listOf(RecordBean), beans), Argument.listOf(RecordBean)) == beans
    }

    void "tabular array with fields header is decoded directly"() {
        given:
        def toon = '''users[2]{id,name,role}:
  1,Alice,Admin
  2,Bob,User
'''

        when:
        def decoded = readToon(toon, Argument.mapOf(Argument.of(String), Argument.listOf(TabularPersonBean)))

        then:
        decoded.users.size() == 2
        decoded.users[0].id() == 1
        decoded.users[0].name() == "Alice"
        decoded.users[0].role() == "Admin"
        decoded.users[1].id() == 2
        decoded.users[1].name() == "Bob"
        decoded.users[1].role() == "User"
    }

    void "keyed tabular array with fields header is decoded directly"() {
        given:
        def toon = '''members[2:]{name,age}:
  lead: Alice,30
  dev: Bob,25
'''

        when:
        def decoded = readToon(toon, Argument.mapOf(Argument.of(String), Argument.mapOf(String, SimpleBean)))

        then:
        decoded.members.size() == 2
        decoded.members.lead.name == "Alice"
        decoded.members.lead.age == 30
        decoded.members.dev.name == "Bob"
        decoded.members.dev.age == 25
    }

    void "list items array syntax is decoded directly"() {
        given:
        def toon = '''items[2]:
  - first
  - second
'''

        when:
        def decoded = readToon(toon, Argument.mapOf(Argument.of(String), Argument.listOf(String)))

        then:
        decoded.items == ["first", "second"]
    }

    void "empty array is decoded to empty collection"() {
        given:
        def toon = "items: []\n"

        when:
        def decoded = readToon(toon, Argument.mapOf(Argument.of(String), Argument.listOf(String)))

        then:
        decoded.items != null
        decoded.items.isEmpty()
    }
}
