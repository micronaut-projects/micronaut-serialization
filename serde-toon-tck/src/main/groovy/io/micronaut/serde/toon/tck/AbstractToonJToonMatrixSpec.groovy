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

import dev.toonformat.jtoon.*
import io.micronaut.core.type.Argument
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Compares the mapper with {@code dev.toonformat:jtoon} over matrices of strings, keys, numbers, delimiters, beans
 * and document edge cases that the spec fixtures do not cover systematically.
 *
 * <p>Write tests check that mapper output is read by jtoon to the same data. Read tests check that jtoon output is
 * read by the mapper to the same data. Document tests feed the same text to both decoders and compare outcomes.</p>
 */
abstract class AbstractToonJToonMatrixSpec extends Specification implements ToonSpec {

    private static final Map<String, Delimiter> DELIMITERS = [',': Delimiter.COMMA, '\t': Delimiter.TAB, '|': Delimiter.PIPE]

    private static final List<String> STRINGS = [
            '', ' ', ' lead', 'trail ', 'a b', '123', '-5', '1e3', '0123', '1.0', '+1', '.5', '0x1F', 'true', 'false', 'null', 'True',
            'a,b', 'a|b', 'a\tb', 'a:b', 'key: value', '-', '- x', '-x', '#c', 'a#b', '[x]', '{x}', '[2]: x', 'a"b', 'a\\b', '"quoted"',
            'line\nbreak', 'cr\rreturn', 'ünï', '日本語', '😀', 'a\u0000b', 'tab\tinside', ' ', '\u00a0nbsp',
    ].unique()

    private static final List<String> KEYS = [
            'a-b', '2key', 'a b', 'a.b', 'a:b', 'a,b', 'a|b', 'a[0]', 'a[]', '{x}', 'x"y', 'ünï', 'true', 'null', '-lead', '#k', 'k ', ' k', '_u', 'A1', 'a/b', '123', '1.5', '',
    ]

    private static final List<Object> NUMBERS = [
            0, 1, -1, Long.MAX_VALUE, Long.MIN_VALUE, new BigInteger('1' + '0' * 30), new BigInteger('-' + '9' * 25),
            new BigDecimal('0.1'), new BigDecimal('-0.1'), new BigDecimal('123456789.123456789'), 1.5d, -2.25d, 0.000001d, 0.0000001d,
            1e20d, 1e21d, 1.5e300d, 1e-300d, Double.MAX_VALUE, Double.MIN_VALUE, 1.1f, 3.4028235e38f,
    ]

    @Unroll
    void "string #label is exchanged with jtoon using delimiter #delimiterName"() {
        given:
        def data = [v: value, inline: [value, 'x'], rows: [[c: value, n: 1], [c: 'y', n: 2]]]
        def properties = ['micronaut.serde.format.toon.delimiter': delimiterName]

        when:
        def written = writeToonWithProperties(properties, data)
        def jtoonRead = JToon.decode(written)
        def jtoonWritten = JToon.encode(data, encodeOptions(DELIMITERS[delimiter]))
        def mapperRead = readToon(jtoonWritten, Argument.of(Object))

        then:
        ToonSpecFixtures.jsonEquals(jtoonRead, data)
        ToonSpecFixtures.jsonEquals(mapperRead, data)

        where:
        [value, delimiter] << [STRINGS, DELIMITERS.keySet()].combinations()
        label = value.inspect()
        delimiterName = DELIMITERS[delimiter].name()
    }

    @Unroll
    void "key #label is exchanged with jtoon"() {
        given:
        def data = [(key): 1, nested: [(key): [(key): 2]], rows: [[(key): 1, other: 'x'], [(key): 2, other: 'y']]]

        when:
        def written = writeToon(data)
        def jtoonRead = JToon.decode(written)
        def jtoonWritten = JToon.encode(data)
        def mapperRead = readToon(jtoonWritten, Argument.of(Object))

        then:
        ToonSpecFixtures.jsonEquals(jtoonRead, data)
        ToonSpecFixtures.jsonEquals(mapperRead, data)

        where:
        key << KEYS
        label = key.inspect()
    }

    /**
     * Numbers that jtoon 2.0.4 does not read back from mapper output as the same value: it decodes integers beyond
     * the long range as strings or doubles, a quoted big integer as a string, and fractions with more than double
     * precision as doubles.
     */
    private static final Set<String> JTOON_READS_NUMBER_DIFFERENTLY = [
            'BigInteger 1000000000000000000000000000000',
            'BigInteger -9999999999999999999999999',
            'BigDecimal 123456789.123456789',
            'Double 1.0E20',
    ] as Set<String>

    /**
     * Numbers that the mapper does not read back from jtoon output as the same value: jtoon writes a float widened
     * to a double, and writes integers beyond the long range as quoted strings.
     */
    private static final Set<String> MAPPER_READS_NUMBER_DIFFERENTLY = [
            'BigInteger 1000000000000000000000000000000',
            'BigInteger -9999999999999999999999999',
            'Float 1.1',
            'Float 3.4028235E38',
    ] as Set<String>

    @Unroll
    void "number #label written by mapper is read by jtoon"() {
        given:
        def data = [v: number, inline: [number, 1], rows: [[n: number, i: 1], [n: number, i: 2]]]

        when:
        def jtoonRead = JToon.decode(writeToon(data))

        then:
        numbersEqual(jtoonRead, data) == !JTOON_READS_NUMBER_DIFFERENTLY.contains(label)

        where:
        number << NUMBERS
        label = "${number.class.simpleName} ${number}".toString()
    }

    @Unroll
    void "number #label written by jtoon is read by mapper"() {
        given:
        def data = [v: number, inline: [number, 1], rows: [[n: number, i: 1], [n: number, i: 2]]]

        when:
        def mapperRead = readToon(JToon.encode(data), Argument.of(Object))

        then:
        numbersEqual(mapperRead, data) == !MAPPER_READS_NUMBER_DIFFERENTLY.contains(label)

        where:
        number << NUMBERS
        label = "${number.class.simpleName} ${number}".toString()
    }

    @Unroll
    void "big integer #number is written with the same text by mapper and jtoon"() {
        expect:
        writeToon([v: number]) == JToon.encode([v: number])

        where:
        number << [BigInteger.valueOf(Long.MAX_VALUE), BigInteger.valueOf(Long.MAX_VALUE) + 1, new BigInteger('9' * 21), new BigInteger('1' + '0' * 30), new BigInteger('-' + '9' * 25)]
    }

    @Unroll
    void "non-finite number #number is written as null and read by jtoon"() {
        when:
        def written = writeToon([v: number])

        then:
        written == 'v: null'
        JToon.decode(written) == [v: null]

        where:
        number << [Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Float.NaN]
    }

    @Unroll
    void "beans are exchanged with jtoon using delimiter #delimiter"() {
        given:
        def properties = ['micronaut.serde.format.toon.delimiter': DELIMITERS[delimiter].name()]
        def beans = [
                new RecordBean('Ref | Parity, "quoted"', 42),
                new NullableBean('required', null),
                new NullableBean('required', 'optional'),
                new ScalarsBean('text', true, 7, 1.5d, null),
                new ObjectBean('outer', new SimpleBean('inner', 3)),
                new CollectionsBean(['a', 'b,c'], [x: 1, y: 2], [new SimpleBean('one', 1), new SimpleBean('two', 2)]),
                new CollectionsBean(null, null, null),
                new NumbersBean(1, Long.MAX_VALUE, 1.5d, 2.5f, new BigInteger('1' + '0' * 30), new BigDecimal('123.456')),
                new ContactsBean(new AddressBean('1 Main St', 'Springfield'), new AddressBean('2 Work Ave', 'Shelbyville')),
        ]

        expect:
        beans.every { bean ->
            Class type = bean.getClass()
            def written = writeToonWithProperties(properties, bean)
            def viaMapper = readToon(written, Argument.of(Object))
            def viaJToon = JToon.decode(written)
            def jtoonWritten = JToon.encode(viaMapper, encodeOptions(DELIMITERS[delimiter]))
            ToonSpecFixtures.jsonEquals(viaJToon, viaMapper) && readToon(jtoonWritten, Argument.of(type)) == bean
        }

        where:
        delimiter << DELIMITERS.keySet()
    }

    @Unroll
    void "collections of beans are exchanged with jtoon using delimiter #delimiter"() {
        given:
        def properties = ['micronaut.serde.format.toon.delimiter': DELIMITERS[delimiter].name()]
        def residents = [
                new ResidentBean(1, new AddressBean('1 Main St', 'Springfield')),
                new ResidentBean(2, new AddressBean('2 Elm, St', 'Shelby|ville')),
        ]
        def people = [alice: new TabularPersonBean(1, 'Alice', 'Admin'), bob: new TabularPersonBean(2, 'Bob', 'User')]

        when:
        def writtenResidents = writeToonWithProperties(properties, residents)
        def writtenPeople = writeToonWithProperties(properties, people)
        def jtoonResidents = JToon.encode(JToon.decode(writtenResidents), encodeOptions(DELIMITERS[delimiter]))
        def jtoonPeople = JToon.encode(JToon.decode(writtenPeople), encodeOptions(DELIMITERS[delimiter]))

        then:
        readToon(writtenResidents, Argument.listOf(ResidentBean)) == residents
        readToon(jtoonResidents, Argument.listOf(ResidentBean)) == residents
        readToon(writtenPeople, Argument.mapOf(Argument.of(String), Argument.of(TabularPersonBean))) == people
        readToon(jtoonPeople, Argument.mapOf(Argument.of(String), Argument.of(TabularPersonBean))) == people

        where:
        delimiter << DELIMITERS.keySet()
    }

    void "large mapper output stays within the default jtoon limits"() {
        given:
        def rows = (0..<5000).collect { [id: it, name: "name ${it}".toString(), tags: ['a', 'b']] }
        def deep = [v: 1]
        200.times { deep = [n: deep] }

        expect:
        ToonSpecFixtures.jsonEquals(JToon.decode(writeToon([rows: rows])), [rows: rows])
        ToonSpecFixtures.jsonEquals(JToon.decode(writeToon(deep)), deep)
    }

    @Unroll
    void "jtoon length marker output is accepted by jtoon and rejected by the mapper: #label"() {
        given:
        def text = JToon.encode(data, new EncodeOptions(2, Delimiter.COMMA, true, KeyFolding.OFF, Integer.MAX_VALUE))

        expect:
        text.contains('[#')
        JToon.decode(text) == data
        attempt { readToon(text, Argument.of(Object)) }.error != null

        where:
        data                             | label
        [a: [1, 2, 3]]                   | 'inline array'
        [a: [[x: 1], [x: 2]]]            | 'tabular array'
        [a: [[x: 1, y: [1, 2]], [x: 2]]] | 'list array'
    }

    void "jtoon key folding output is read as literal keys by both decoders"() {
        given:
        def data = [a: [b: [c: 1]], d: [e: 'x']]
        def text = JToon.encode(data, new EncodeOptions(2, Delimiter.COMMA, false, KeyFolding.SAFE, Integer.MAX_VALUE))

        expect:
        text.contains('a.b.c: 1')
        sameOutcome(text)
        ToonSpecFixtures.jsonEquals(readToon(text, Argument.of(Object)), ['a.b.c': 1, 'd.e': 'x'])
    }

    @Unroll
    void "document edge case is decoded the same by mapper and jtoon: #label"() {
        expect:
        sameOutcome(text)

        where:
        text                               | label
        'a: 1\r\nb: 2'                     | 'CRLF line endings'
        'a: 1\nb: 2\n'                     | 'trailing newline'
        'a: 1\nb: 2\n\n\n'                 | 'trailing blank lines'
        'a: 1   \nb: 2'                    | 'trailing spaces'
        '\uFEFFa: 1'                       | 'byte order mark'
        'a: ü\nb: 日本語\nc: 😀'            | 'multi-byte characters'
        'rows[2]{a,b}:\n  ü,日本語\n  😀,x' | 'multi-byte tabular cells'
        '42'                               | 'root number'
        'hello'                            | 'root string'
        '"quoted root"'                    | 'root quoted string'
        'true'                             | 'root boolean'
        'null'                             | 'root null'
        ''                                 | 'empty document'
        '\n'                               | 'blank document'
        '[0]:'                             | 'root empty array'
        '[3]: 1,2,3'                       | 'root inline array'
        '[2]{a,b}:\n  1,2\n  3,4'          | 'root tabular array'
        'a[2]: 1'                          | 'declared length longer than values'
        'a[1]: 1,2'                        | 'declared length shorter than values'
        'a[2]{x,y}:\n  1,2'                | 'declared length longer than rows'
        'a[1]{x,y}:\n  1,2\n  3,4'         | 'declared length shorter than rows'
        'a[1]{x,y}:\n  1'                  | 'row with fewer cells than fields'
        'a[1]{x,y}:\n  1,2,3'              | 'row with more cells than fields'
        'a : 1'                                    | 'space before the colon'
        '"a" : 1'                                  | 'space before the colon of a quoted key'
        'a:\n  b : 1'                              | 'space before the colon of a nested key'
        'a [2]: 1,2'                               | 'space before the bracket'
        'a[2]{ x, y}:\n  1,2\n  3,4'               | 'spaces inside a field list'
        'a[1]{x ,y}:\n  1 , 2'                      | 'spaces around field names and cells'
        'm[2:]{v}:\n  a : 1\n  b : 2'              | 'space before the colon of a keyed entry'
        'a: 1\na: 2'                       | 'duplicate keys'
        'a:\n  b: 1\n  b: 2'               | 'nested duplicate keys'
        'a: 1\n  b: 2'                     | 'indented line after a scalar field'
        'a: "unterminated'                 | 'unterminated string'
        'a: "bad \\q escape"'              | 'invalid escape'
        'a: 007'                           | 'leading zero number'
        'a: -0'                            | 'negative zero'
        'a: 1e2'                           | 'exponent number'
        'a: 1_000'                         | 'underscore number'
        'a: 0x10'                          | 'hex number'
        'a: Infinity'                      | 'infinity literal'
        'a:b'                              | 'colon without space'
        '\ta: 1'                           | 'tab indentation'
        ' a: 1'                            | 'single leading space'
        'a: 1 # trailing comment'          | 'trailing comment text'
        '# only a comment'                 | 'comment only document'
        '- a\n- b'                         | 'root list without header'
    }

    void "whitespace before a nested field group is rejected by the mapper and accepted by jtoon"() {
        given:
        def text = 'a[1]{x {y}}:\n  1'

        expect:
        attempt { readToon(text, Argument.of(Object)) }.error != null
        JToon.decode(text) == [a: [[x: [y: 1]]]]
    }

    /**
     * Decodes {@code text} with both implementations in strict mode and asserts the outcomes agree: both fail, or
     * both succeed with equal data.
     */
    protected boolean sameOutcome(String text) {
        def mapper = attempt { readToon(text, Argument.of(Object)) }
        def jtoon = attempt { JToon.decode(text, DecodeOptions.withStrict(true)) }
        if (mapper.error != null || jtoon.error != null) {
            assert (mapper.error != null) == (jtoon.error != null): "mapper ${mapper.error ?: mapper.value.inspect()}, jtoon ${jtoon.error ?: jtoon.value.inspect()}"
            return true
        }
        assert numbersEqual(mapper.value, jtoon.value): "mapper ${mapper.value.inspect()}, jtoon ${jtoon.value.inspect()}"
        true
    }

    private static EncodeOptions encodeOptions(Delimiter delimiter) {
        new EncodeOptions(2, delimiter, false, KeyFolding.OFF, Integer.MAX_VALUE)
    }

    private static boolean numbersEqual(Object actual, Object expected) {
        ToonSpecFixtures.jsonEquals(actual, expected)
    }

    private static Map<String, Object> attempt(Closure<Object> action) {
        try {
            return [value: action.call(), error: null]
        } catch (Exception e) {
            return [value: null, error: e]
        }
    }
}
