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

import dev.toonformat.jtoon.JToon
import io.micronaut.core.type.Argument
import spock.lang.Specification

/**
 * Validates spec compatibility and round-trip parity against the reference {@code dev.toonformat:jtoon} (toon-java) implementation.
 */
abstract class AbstractToonJToonParitySpec extends Specification implements ToonSpec {

    void "output written by mapper is readable by jtoon"() {
        given:
        def bean = new RecordBean("Ref Parity", 42)

        when:
        def toon = writeToon(bean)
        def jtoonDecoded = JToon.decode(toon)

        then:
        jtoonDecoded instanceof Map
        jtoonDecoded.title == "Ref Parity"
        jtoonDecoded.pages == 42
    }

    void "tabular output written by jtoon is readable by mapper"() {
        given:
        def data = [
                users: [
                        [id: 1, name: "Alice", role: "Admin"],
                        [id: 2, name: "Bob", role: "User"]
                ]
        ]
        def jtoonEncoded = JToon.encode(data)

        when:
        def mapperDecoded = readToon(jtoonEncoded, Argument.mapOf(Argument.of(String), Argument.listOf(TabularPersonBean)))

        then:
        mapperDecoded.users.size() == 2
        mapperDecoded.users[0].id() == 1
        mapperDecoded.users[0].name() == "Alice"
        mapperDecoded.users[0].role() == "Admin"
        mapperDecoded.users[1].id() == 2
        mapperDecoded.users[1].name() == "Bob"
        mapperDecoded.users[1].role() == "User"
    }

    void "complex collections round-trip across mapper and jtoon"() {
        given:
        def data = [
                values: ["first", "second", "third"],
                counts: [a: 10, b: 20]
        ]
        def jtoonEncoded = JToon.encode(data)

        when:
        def mapperDecoded = readToon(jtoonEncoded, CollectionsBean)
        def reEncoded = writeToon(mapperDecoded)
        def jtoonRoundTrip = JToon.decode(reEncoded)

        then:
        mapperDecoded.values() == ["first", "second", "third"]
        mapperDecoded.counts() == [a: 10, b: 20]
        jtoonRoundTrip.values == ["first", "second", "third"]
        jtoonRoundTrip.counts.a == 10
        jtoonRoundTrip.counts.b == 20
    }
}
