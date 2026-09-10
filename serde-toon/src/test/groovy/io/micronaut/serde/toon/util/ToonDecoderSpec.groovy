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
package io.micronaut.serde.toon.util

import io.micronaut.json.tree.JsonNode
import io.micronaut.serde.exceptions.SerdeException
import io.micronaut.serde.toon.SerdeToonConfiguration
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.charset.StandardCharsets

/**
 * Direct unit tests of {@link ToonDecoder} against literal TOON text,
 * plus round-trip tests (encode via {@link ToonEncoder}, decode back, encode
 * again, and compare the two encoded texts) reusing the shapes covered by
 * {@link ToonEncoderSpec} - a text-equality round trip catches value/typing
 * mistakes too, since a wrongly-decoded value almost always re-encodes
 * differently (e.g. a number misread as a string re-encodes quoted).
 */
class ToonDecoderSpec extends Specification {

    private static ToonDecoder adapter(int indent = 2) {
        def config = new SerdeToonConfiguration()
        config.indent = indent
        new ToonDecoder()
    }

    private static ToonEncoder writer(SerdeToonConfiguration.Delimiter delimiter = SerdeToonConfiguration.Delimiter.COMMA, int indent = 2) {
        def config = new SerdeToonConfiguration()
        config.delimiter = delimiter
        config.indent = indent
        new ToonEncoder(config)
    }

    private static JsonNode parse(String text, ToonDecoder parser = adapter()) {
        parser.parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)))
    }

    private static String encode(ToonEncoder writer, JsonNode tree) {
        def out = new ByteArrayOutputStream()
        writer.write(out, tree)
        out.toString(StandardCharsets.UTF_8)
    }

    /**
     * Encodes {@code tree}, decodes the result, re-encodes the decoded tree,
     * and asserts the two encoded texts are identical.
     */
    private static String assertRoundTrips(JsonNode tree, ToonEncoder writer = writer(), ToonDecoder adapter = adapter()) {
        String text = encode(writer, tree)
        JsonNode decoded = parse(text, adapter)
        String reEncoded = encode(writer, decoded)
        assert reEncoded == text
        return text
    }

    void 'test decoding a flat object with primitive fields'() {
        given:
        def tree = parse('name: Alice\nage: 30')

        expect:
        tree.get('name').stringValue == 'Alice'
        tree.get('age').numberValue == 30L
    }

    void 'test decoding preserves scalar types, not just strings'() {
        given:
        def tree = parse('''n: 42
b: true
z: null
s: "true"
q: "123"''')

        expect:
        tree.get('n').isNumber()
        tree.get('n').intValue == 42
        tree.get('b').isBoolean()
        tree.get('b').booleanValue
        tree.get('z').isNull()
        tree.get('s').isString()
        tree.get('s').stringValue == 'true'
        tree.get('q').isString()
        tree.get('q').stringValue == '123'
    }

    void 'test decoding unescapes a quoted string'() {
        given:
        def tree = parse('value: "line1\\nline2\\ttabbed\\\\slash"')

        expect:
        tree.get('value').stringValue == 'line1\nline2\ttabbed\\slash'
    }

    void 'test decoding a nested object'() {
        given:
        def tree = parse('''address:
  city: Springfield
  zip: "12345"''')

        expect:
        tree.get('address').get('city').stringValue == 'Springfield'
        tree.get('address').get('zip').stringValue == '12345'
    }

    void 'test decoding the weather-forecast tabular example'() {
        given:
        def tree = parse('''location: Springfield
forecast[2]{day,temp,condition}:
  Mon,68,Sunny
  Tue,72,Cloudy''')

        expect:
        tree.get('location').stringValue == 'Springfield'
        tree.get('forecast').size() == 2
        tree.get('forecast').get(0).get('day').stringValue == 'Mon'
        tree.get('forecast').get(0).get('temp').intValue == 68
        tree.get('forecast').get(0).get('condition').stringValue == 'Sunny'
        tree.get('forecast').get(1).get('day').stringValue == 'Tue'
        tree.get('forecast').get(1).get('temp').intValue == 72
        tree.get('forecast').get(1).get('condition').stringValue == 'Cloudy'
    }

    void 'test decoding an empty document is an empty object'() {
        expect:
        parse('').size() == 0
        parse('   \n   ').size() == 0
    }

    void 'test full-line comments are ignored'() {
        given:
        def withComments = parse('''# a leading comment
name: Alice
# a comment between fields
age: 30
# a trailing comment''')
        def withoutComments = parse('name: Alice\nage: 30')

        expect:
        withComments.get('name').stringValue == withoutComments.get('name').stringValue
        withComments.get('age').intValue == withoutComments.get('age').intValue
    }

    @Unroll
    void 'test round-tripping #description'() {
        expect:
        assertRoundTrips(tree) == expectedText

        where:
        description                       | tree                                                                                                                                                          | expectedText
        'a flat object'                   | JsonNode.createObjectNode([name: JsonNode.createStringNode('Alice'), age: JsonNode.createNumberNode(30)])                                                     | 'name: Alice\nage: 30'
        'a nested object'                 | JsonNode.createObjectNode([address: JsonNode.createObjectNode([city: JsonNode.createStringNode('Springfield'), zip: JsonNode.createStringNode('12345')])])    | 'address:\n  city: Springfield\n  zip: "12345"'
        'an inline primitive array'       | JsonNode.createObjectNode([tags: JsonNode.createArrayNode([JsonNode.createStringNode('a'), JsonNode.createStringNode('b'), JsonNode.createStringNode('c')])]) | 'tags[3]: a,b,c'
        'an empty array and empty object' | JsonNode.createObjectNode([tags: JsonNode.createArrayNode([]), meta: JsonNode.createObjectNode([:])])                                                         | 'tags: []\nmeta:'
        'a root array'                    | JsonNode.createArrayNode([JsonNode.createStringNode('a'), JsonNode.createStringNode('b')])                                                                    | '[2]: a,b'
        'a root string'                   | JsonNode.createStringNode('hi')                                                                                                                               | 'hi'
        'a root number'                   | JsonNode.createNumberNode(42)                                                                                                                                 | '42'
    }

    void 'test round-tripping a uniform array of records (tabular form)'() {
        given:
        def tree = JsonNode.createObjectNode([
                location: JsonNode.createStringNode('Springfield'),
                forecast: JsonNode.createArrayNode([
                        JsonNode.createObjectNode([day: JsonNode.createStringNode('Mon'), temp: JsonNode.createNumberNode(68), condition: JsonNode.createStringNode('Sunny')]),
                        JsonNode.createObjectNode([day: JsonNode.createStringNode('Tue'), temp: JsonNode.createNumberNode(72), condition: JsonNode.createStringNode('Cloudy')])
                ])
        ])

        expect:
        assertRoundTrips(tree) == '''location: Springfield
forecast[2]{day,temp,condition}:
  Mon,68,Sunny
  Tue,72,Cloudy'''
    }

    void 'test round-tripping a uniform nested-object column (folded header)'() {
        given:
        def tree = JsonNode.createObjectNode([
                readings: JsonNode.createArrayNode([
                        JsonNode.createObjectNode([day: JsonNode.createStringNode('Mon'), temp: JsonNode.createObjectNode([min: JsonNode.createNumberNode(60), max: JsonNode.createNumberNode(70)])]),
                        JsonNode.createObjectNode([day: JsonNode.createStringNode('Tue'), temp: JsonNode.createObjectNode([min: JsonNode.createNumberNode(65), max: JsonNode.createNumberNode(75)])])
                ])
        ])

        expect:
        assertRoundTrips(tree) == '''readings[2]{day,temp{min,max}}:
  Mon,60,70
  Tue,65,75'''
    }

    void 'test round-tripping a map of uniform records (keyed tabular form)'() {
        given:
        def tree = JsonNode.createObjectNode([
                envs: JsonNode.createObjectNode([
                        prod: JsonNode.createObjectNode([region: JsonNode.createStringNode('us'), replicas: JsonNode.createNumberNode(3)]),
                        dev : JsonNode.createObjectNode([region: JsonNode.createStringNode('eu'), replicas: JsonNode.createNumberNode(1)])
                ])
        ])

        expect:
        assertRoundTrips(tree) == '''envs[2:]{region,replicas}:
  prod: us,3
  dev: eu,1'''
    }

    void 'test round-tripping a non-uniform array (list form)'() {
        given:
        def tree = JsonNode.createObjectNode([
                items: JsonNode.createArrayNode([
                        JsonNode.createStringNode('text'),
                        JsonNode.createObjectNode([id: JsonNode.createNumberNode(1)])
                ])
        ])

        expect:
        assertRoundTrips(tree) == '''items[2]:
  - text
  - id: 1'''
    }

    void 'test round-tripping an array of arrays'() {
        given:
        def tree = JsonNode.createObjectNode([
                matrix: JsonNode.createArrayNode([
                        JsonNode.createArrayNode([JsonNode.createNumberNode(1), JsonNode.createNumberNode(2)]),
                        JsonNode.createArrayNode([JsonNode.createNumberNode(3), JsonNode.createNumberNode(4)])
                ])
        ])

        expect:
        assertRoundTrips(tree) == '''matrix[2]:
  - [2]: 1,2
  - [2]: 3,4'''
    }

    void 'test round-tripping an empty array as a list item'() {
        given:
        def tree = JsonNode.createObjectNode([
                items: JsonNode.createArrayNode([JsonNode.createArrayNode([]), JsonNode.createStringNode('text')])
        ])

        expect:
        assertRoundTrips(tree) == '''items[2]:
  - [0]:
  - text'''
    }

    @Unroll
    void 'test round-tripping quoted string edge cases: #description'() {
        given:
        def tree = JsonNode.createObjectNode([value: JsonNode.createStringNode(input)])

        expect:
        assertRoundTrips(tree) == "value: ${expected}"

        where:
        description              | input      | expected
        'looks like a boolean'   | 'true'     | '"true"'
        'looks like a number'    | '123'      | '"123"'
        'starts with a hyphen'   | '-5apples' | '"-5apples"'
        'contains the delimiter' | 'a,b'      | '"a,b"'
        'empty string'           | ''         | '""'
    }

    void 'test round-tripping with a non-default indent size'() {
        given:
        def tree = JsonNode.createObjectNode([
                items: JsonNode.createArrayNode([
                        JsonNode.createStringNode('text'),
                        JsonNode.createObjectNode([id: JsonNode.createNumberNode(1)])
                ])
        ])
        def w = writer(SerdeToonConfiguration.Delimiter.COMMA, 4)
        def a = adapter(4)

        expect:
        assertRoundTrips(tree, w, a) == '''items[2]:
    - text
    - id: 1'''
    }

    void 'test decoding a 4-space-indented document with a decoder configured for indent 2 (indent is inferred, not configured)'() {
        given:
        // Written with a 4-space indent, decoded with an adapter configured
        // for the default indent (2): indentation is inferred from the
        // document, so the decoder's own configured indent has no effect.
        def text = writer(SerdeToonConfiguration.Delimiter.COMMA, 4).with {
            def out = new ByteArrayOutputStream()
            it.write(out, JsonNode.createObjectNode([
                    address: JsonNode.createObjectNode([city: JsonNode.createStringNode('Springfield')])
            ]))
            out.toString(StandardCharsets.UTF_8)
        }

        expect:
        text == 'address:\n    city: Springfield'
        parse(text, adapter(2)).get('address').get('city').stringValue == 'Springfield'
    }

    void 'test inconsistent sibling indentation is a strict decode error'() {
        when:
        // "age" is indented one space deeper than "name", its sibling.
        parse('address:\n  name: Alice\n   age: 30')

        then:
        thrown(SerdeException)
    }

    void 'test decoding tab and pipe delimited headers (delimiter is read per-header, not configured)'() {
        expect:
        parse('tags[2\t]: a\tb').get('tags').values().toList()*.stringValue == ['a', 'b']
        parse('tags[2|]: a|b').get('tags').values().toList()*.stringValue == ['a', 'b']
    }

    @Unroll
    void 'test strict decode errors: #description'() {
        when:
        parse(text)

        then:
        thrown(SerdeException)

        where:
        description                                    | text
        'inline array declares more values than given' | 'tags[3]: a,b'
        'tabular header declares more rows than given' | 'items[2]{a}:\n  1'
        'tabular row has the wrong number of cells'    | 'items[2]{a,b}:\n  1,2\n  3'
        'duplicate field name in a field list'         | 'items[2]{a,a}:\n  1,2\n  3,4'
        'malformed header missing a closing bracket'   | 'tags[3: a,b,c'
        'unterminated quoted string'                   | 'value: "unterminated'
        'duplicate object field key'                   | 'name: Alice\nname: Bob'
        'duplicate keyed-tabular entry key'            | 'envs[2:]{region}:\n  prod: us\n  prod: eu'
        'indented root content'                        | '  name: Alice'
        'space before colon in unquoted key'           | 'name : Alice'
        'space before array header bracket'            | 'items [2]: 1,2'
        'space before colon in quoted key'             | '"name" : Alice'
        'invalid character starting unquoted key'      | '123: Alice'
        'space inside unquoted key'                    | 'foo bar: baz'
        'invalid unquoted field name in field list'    | 'items[2]{123}:\n  1\n  2'
        'whitespace around unquoted field in list'     | 'items[2]{ id, name}:\n  1, Alice\n  2, Bob'
        'huge declared tabular row count'              | 'items[2000000000]{a}:\n  1'
        'huge declared list item count'                | 'items[2000000000]:\n  - a'
    }

    void 'test extra spaces after a colon are trimmed, not treated as part of the value'() {
        expect:
        parse('count:  5').get('count').intValue == 5
        parse('address:\n  city:   Springfield').get('address').get('city').stringValue == 'Springfield'
    }

    void 'test extra spaces after a list item hyphen are trimmed'() {
        expect:
        parse('items[1]:\n  -   text').get('items').get(0).stringValue == 'text'
    }

    void 'test a trailing tab after a value is stripped'() {
        expect:
        parse("name: Alice\t").get('name').stringValue == 'Alice'
    }

    @Unroll
    void 'test ToonEscapes.stripTrailingWhitespace: "#input"'() {
        expect:
        ToonEscapes.stripTrailingWhitespace(input) == expected

        where:
        input        | expected
        ''           | ''
        'abc'        | 'abc'
        'abc '       | 'abc'
        'abc\t'      | 'abc'
        'abc  \t \t' | 'abc'
        '  abc  \t'  | '  abc'
        '   '        | ''
        '\t\t'       | ''
    }

    @Unroll
    void 'test ToonEscapes.countLeadingSpaces: "#input"'() {
        expect:
        ToonEscapes.countLeadingSpaces(input) == expected

        where:
        input          | expected
        ''             | 0
        'abc'          | 0
        '  abc'        | 2
        '    key: val' | 4
        '   '          | 3
    }

    void 'test ToonEscapes.countLeadingSpaces throws on tab indentation'() {
        when:
        ToonEscapes.countLeadingSpaces('\tabc')

        then:
        thrown(SerdeException)

        when:
        ToonEscapes.countLeadingSpaces('  \tabc')

        then:
        thrown(SerdeException)
    }

    @Unroll
    void 'test ToonEscapes.isCommentLine: "#input"'() {
        expect:
        ToonEscapes.isCommentLine(input) == expected
        ToonEscapes.isCommentLine(input, leadingSpaces) == expected

        where:
        input          | leadingSpaces | expected
        '# comment'    | 0             | true
        '  # indented' | 2             | true
        '#comment'     | 0             | true
        'key: value'   | 0             | false
        '  key: value' | 2             | false
        ''             | 0             | false
        '   '          | 3             | false
    }

    @Unroll
    void 'test ToonEscapes.looksLikeScalar: "#input"'() {
        expect:
        ToonEscapes.looksLikeScalar(input) == expected

        where:
        input                | expected
        'true'               | true
        'false'              | true
        'null'               | true
        '42'                 | true
        'hello'              | true
        '"quoted string"'    | true
        '"has:colon"'        | true
        '"has[bracket]"'     | true
        'key: value'         | false
        '"quotedKey": value' | false
        'key:'               | false
        '[]'                 | false
        '[2]:'               | false
        '[2]{a,b}:'          | false
        'items[2]:'          | false
    }

    void 'test ToonEscapes.looksLikeScalar throws on unterminated quoted string'() {
        when:
        ToonEscapes.looksLikeScalar('"unterminated')

        then:
        thrown(SerdeException)
    }

    @Unroll
    void 'test ToonEscapes.isValidUnquotedKey: "#key"'() {
        expect:
        ToonEscapes.isValidUnquotedKey(key) == expected

        where:
        key       | expected
        'name'    | true
        'a_b'     | true
        '_key'    | true
        'user.id' | true
        'item1'   | true
        ''        | false
        '123'     | false
        'foo bar' | false
        'foo '    | false
        ' foo'    | false
        'foo:bar' | false
    }
}
