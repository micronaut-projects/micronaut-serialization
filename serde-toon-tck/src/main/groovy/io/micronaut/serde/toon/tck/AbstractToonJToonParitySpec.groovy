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

    void "keyed-tabular output for a bean with same-typed object fields is readable by jtoon"() {
        given:
        def bean = new ContactsBean(new AddressBean("1 Main St", "Springfield"), new AddressBean("2 Work Ave", "Shelbyville"))

        when:
        def toon = writeToon(bean)
        def jtoonDecoded = JToon.decode(toon)

        then:
        jtoonDecoded.home == [street: "1 Main St", city: "Springfield"]
        jtoonDecoded.work == [street: "2 Work Ave", city: "Shelbyville"]
    }

    void "keyed-tabular output for a map of beans is readable by jtoon"() {
        given:
        def argument = Argument.mapOf(Argument.of(String), Argument.of(TabularPersonBean))
        def map = [alice: new TabularPersonBean(1, "Alice", "Admin"), bob: new TabularPersonBean(2, "Bob", "User")]

        when:
        def toon = writeToon(argument, map)
        def jtoonDecoded = JToon.decode(toon)

        then:
        jtoonDecoded.alice == [id: 1, name: "Alice", role: "Admin"]
        jtoonDecoded.bob == [id: 2, name: "Bob", role: "User"]
    }

    void "keyed-tabular output written by jtoon is readable by mapper"() {
        given:
        def jtoonEncoded = JToon.encode([
                alice: [id: 1, name: "Alice", role: "Admin"],
                bob  : [id: 2, name: "Bob", role: "User"]
        ])

        when:
        def mapperDecoded = readToon(jtoonEncoded, Argument.mapOf(Argument.of(String), Argument.of(TabularPersonBean)))

        then:
        mapperDecoded.alice == new TabularPersonBean(1, "Alice", "Admin")
        mapperDecoded.bob == new TabularPersonBean(2, "Bob", "User")
    }

    void "nested field groups written by mapper are readable by jtoon"() {
        given:
        def argument = Argument.listOf(ResidentBean)
        def residents = [
                new ResidentBean(1, new AddressBean("1 Main St", "Springfield")),
                new ResidentBean(2, new AddressBean("2 Elm St", "Shelbyville"))
        ]

        when:
        def toon = writeToon(argument, residents)
        def jtoonDecoded = JToon.decode(toon)

        then:
        jtoonDecoded == [
                [id: 1, address: [street: "1 Main St", city: "Springfield"]],
                [id: 2, address: [street: "2 Elm St", city: "Shelbyville"]]
        ]
    }

    void "nested field groups written by jtoon are readable by mapper"() {
        given:
        def jtoonEncoded = JToon.encode([
                [id: 1, address: [street: "1 Main St", city: "Springfield"]],
                [id: 2, address: [street: "2 Elm St", city: "Shelbyville"]]
        ])

        when:
        def mapperDecoded = readToon(jtoonEncoded, Argument.listOf(ResidentBean))

        then:
        mapperDecoded == [
                new ResidentBean(1, new AddressBean("1 Main St", "Springfield")),
                new ResidentBean(2, new AddressBean("2 Elm St", "Shelbyville"))
        ]
    }

    void "numbers outside the plain decimal range are exchanged with jtoon"() {
        given:
        def bean = new MeasurementBean(1.5e21d, 2.5e-7d)

        when:
        def toon = writeToon(bean)
        def jtoonDecoded = JToon.decode(toon)
        def mapperDecodedJToon = readToon(JToon.encode([large: 1.5e21d, small: 2.5e-7d]), MeasurementBean)

        then:
        toon.contains("1.5e+21")
        toon.contains("2.5e-7")
        (jtoonDecoded.large as BigDecimal) == new BigDecimal("1.5e21")
        (jtoonDecoded.small as BigDecimal) == new BigDecimal("2.5e-7")
        mapperDecodedJToon == bean
    }

    void "a document with comment lines is read the same by mapper and jtoon"() {
        given:
        def toon = "# leading comment\ntitle: Ref Parity\n# inner comment\npages: 42"

        when:
        def mapperDecoded = readToon(toon, RecordBean)
        def jtoonDecoded = JToon.decode(toon)

        then:
        mapperDecoded == new RecordBean("Ref Parity", 42)
        jtoonDecoded == [title: "Ref Parity", pages: 42]
    }
}
