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

import dev.toonformat.jtoon.DecodeOptions
import dev.toonformat.jtoon.Delimiter
import dev.toonformat.jtoon.EncodeOptions
import dev.toonformat.jtoon.JToon
import dev.toonformat.jtoon.KeyFolding
import dev.toonformat.jtoon.PathExpansion
import io.micronaut.core.type.Argument
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Validates spec compatibility and round-trip parity against the reference {@code dev.toonformat:jtoon} (toon-java) implementation.
 */
abstract class AbstractToonJToonParitySpec extends Specification implements ToonSpec {

    /**
     * Spec encode fixtures whose expected output jtoon 2.0.4 does not read back to the fixture's input.
     * jtoon decodes {@code data: []} in a list item as the string {@code "[]"} rather than an empty array,
     * although its own encoder emits that form.
     */
    private static final Set<String> JTOON_DECODE_DIVERGENCES = [
            'arrays-objects.json :: encodes objects with empty arrays in list form',
            'arrays-objects.json :: places empty arrays on hyphen line when first',
    ] as Set<String>

    /**
     * Spec decode fixtures excluded from the mapper's own fixture suite that the mapper nonetheless accepts: the
     * document indent step is inferred rather than validated against the configured one, so the fixtures expecting
     * an indentation error are accepted, and two valid indentation inputs decode correctly.
     */
    private static final Set<String> MAPPER_ACCEPTS_EXCLUDED = [
            'indentation-errors.json :: throws on object field with non-multiple indentation (3 spaces with indentSize 2)',
            'indentation-errors.json :: throws on list item with non-multiple indentation (3 spaces with indentSize 2)',
            'indentation-errors.json :: throws on non-multiple indentation with custom indentSize 4 (3 spaces)',
            'indentation-errors.json :: throws on depth jump of more than one level',
            'indentation-errors.json :: accepts correct indentation with custom indentSize (4 spaces with indentSize 4)',
            'indentation-errors.json :: accepts non-multiple indentation when strict=false',
    ] as Set<String>

    /**
     * Spec decode fixtures with a valid input that jtoon 2.0.4 does not decode to the fixture's expected value.
     */
    private static final Set<String> JTOON_DECODE_VALID_DIVERGENCES = [
    ] as Set<String>

    /**
     * Spec decode fixtures with an invalid input that jtoon 2.0.4 accepts.
     */
    private static final Set<String> JTOON_ACCEPTS_INVALID = [
    ] as Set<String>

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

    @Unroll
    void "spec encode fixture written by mapper is read by jtoon: #fixture"() {
        given:
        Map<String, Object> options = fixture.options ?: [:]
        Map<String, Object> properties = [:]
        if (options.delimiter) {
            properties['micronaut.serde.format.toon.delimiter'] = options.delimiter
        }
        if (options.indentSize) {
            properties['micronaut.serde.format.toon.indent'] = options.indentSize
        }

        when:
        String toon = properties ? writeToonWithProperties(properties, fixture.input) : writeToon(fixture.input)
        Object decoded = JToon.decode(toon, DecodeOptions.withIndent((options.indentSize ?: 2) as int))

        then:
        ToonSpecFixtures.jsonEquals(decoded, fixture.input)

        where:
        fixture << ToonSpecFixtures.encodeCases().findAll { !JTOON_DECODE_DIVERGENCES.contains(it.toString()) }
    }

    @Unroll
    void "spec encode fixture written by jtoon is read by mapper: #fixture"() {
        given:
        Map<String, Object> options = fixture.options ?: [:]
        Delimiter delimiter = options.delimiter ? Delimiter.values().find { it.value == (options.delimiter as String).charAt(0) } : Delimiter.COMMA
        EncodeOptions encodeOptions = new EncodeOptions((options.indentSize ?: 2) as int, delimiter, false, KeyFolding.OFF, Integer.MAX_VALUE)

        when:
        String toon = JToon.encode(fixture.input, encodeOptions)
        Object decoded = readToon(toon, Argument.of(Object))

        then:
        ToonSpecFixtures.jsonEquals(decoded, fixture.input)

        where:
        fixture << ToonSpecFixtures.encodeCases()
    }

    @Unroll
    void "spec decode fixture is read the same by mapper and jtoon: #fixture"() {
        given:
        Map<String, Object> options = fixture.options ?: [:]
        DecodeOptions defaults = DecodeOptions.DEFAULT
        DecodeOptions jtoonOptions = new DecodeOptions((options.indentSize ?: 2) as int, Delimiter.COMMA, options.strict != false,
                PathExpansion.OFF, defaults.maxDepth(), defaults.maxArraySize(), defaults.maxStringLength())

        when:
        def mapper = attempt { readToon(fixture.input as String, Argument.of(Object)) }
        def jtoon = attempt { JToon.decode(fixture.input as String, jtoonOptions) }

        then:
        if (fixture.shouldError) {
            assert mapper.error != null: "mapper accepted invalid input"
            if (JTOON_ACCEPTS_INVALID.contains(fixture.toString())) {
                assert jtoon.error == null: "jtoon now rejects this input, remove it from JTOON_ACCEPTS_INVALID"
            } else {
                assert jtoon.error != null: "jtoon accepted invalid input"
            }
        } else {
            assert mapper.error == null: "mapper failed: ${mapper.error}"
            assert ToonSpecFixtures.jsonEquals(mapper.value, fixture.expected)
            boolean jtoonMatches = jtoon.error == null && ToonSpecFixtures.jsonEquals(jtoon.value, fixture.expected)
            if (JTOON_DECODE_VALID_DIVERGENCES.contains(fixture.toString())) {
                assert !jtoonMatches: "jtoon now decodes this input, remove it from JTOON_DECODE_VALID_DIVERGENCES"
            } else {
                assert jtoonMatches: "jtoon decoded ${jtoon.error ?: jtoon.value}, mapper decoded ${mapper.value}, expected ${fixture.expected}"
            }
        }

        where:
        fixture << ToonSpecFixtures.decodeCases()
    }

    @Unroll
    void "spec decode fixture outside the mapper's decoding modes: #fixture"() {
        given:
        Map<String, Object> options = fixture.options ?: [:]
        DecodeOptions defaults = DecodeOptions.DEFAULT
        DecodeOptions jtoonOptions = new DecodeOptions((options.indentSize ?: 2) as int, Delimiter.COMMA, options.strict != false,
                PathExpansion.OFF, defaults.maxDepth(), defaults.maxArraySize(), defaults.maxStringLength())

        when:
        def mapper = attempt { readToon(fixture.input as String, Argument.of(Object)) }
        def jtoon = attempt { JToon.decode(fixture.input as String, jtoonOptions) }

        then:
        // jtoon follows the fixture, including its strict and indentSize options.
        if (fixture.shouldError) {
            assert jtoon.error != null: "jtoon accepted invalid input"
        } else {
            assert jtoon.error == null: "jtoon failed: ${jtoon.error}"
            assert ToonSpecFixtures.jsonEquals(jtoon.value, fixture.expected)
        }

        and:
        // The mapper has no non-strict mode and infers the indent step, so it rejects most
        // non-strict-only input and accepts input that the fixture's indent rules reject.
        if (MAPPER_ACCEPTS_EXCLUDED.contains(fixture.toString())) {
            assert mapper.error == null: "mapper failed: ${mapper.error}"
            if (!fixture.shouldError) {
                assert ToonSpecFixtures.jsonEquals(mapper.value, fixture.expected)
            }
        } else {
            assert mapper.error != null: "mapper now accepts this input, add it to MAPPER_ACCEPTS_EXCLUDED"
        }

        where:
        fixture << ToonSpecFixtures.excludedDecodeCases()
    }

    private static Map<String, Object> attempt(Closure<Object> action) {
        try {
            return [value: action.call(), error: null]
        } catch (Exception e) {
            return [value: null, error: e]
        }
    }
}
