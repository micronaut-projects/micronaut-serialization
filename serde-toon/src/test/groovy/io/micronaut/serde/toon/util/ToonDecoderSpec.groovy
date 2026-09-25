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
import io.micronaut.serde.LimitingStream
import io.micronaut.serde.config.SerdeConfiguration
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

    @Unroll
    void 'test decoding accepts an unquoted key that does not match the encoder\'s own unquoted-key grammar: #description'() {
        // Decoders accept the literal text before the first unquoted ':'
        // or '[' as a key, even when it wouldn't match the encoder's own
        // unquoted-key pattern (hyphens, a leading digit, or an internal
        // space, none of which the encoder would leave unquoted itself).
        expect:
        tree.get(key).stringValue == value

        where:
        description             | text           | key       | value
        'hyphen in key'         | 'foo-bar: x'   | 'foo-bar' | 'x'
        'leading digit in key'  | '2key: x'      | '2key'    | 'x'
        'internal space in key' | 'foo bar: baz' | 'foo bar' | 'baz'

        tree = parse(text)
    }

    void 'test decoding accepts a non-conforming key in an array header'() {
        expect:
        parse('foo-bar[2]: 1,2').get('foo-bar').values().toList()*.numberValue == [1, 2]
        parse('k[2]: 5,6').get('k').values().toList()*.numberValue == [5, 6]
    }

    void 'test decoding accepts a non-conforming key inside a keyed tabular block'() {
        // Same leniency applies to a keyed tabular entry's own key.
        expect:
        parse('envs[1:]{region}:\n  2key: us').get('envs').get('2key').get('region').stringValue == 'us'
    }

    void 'test a keyed tabular entry key containing a bracket is read literally, not as a nested header'() {
        // Unlike an object field's key, an entry line has no header syntax
        // of its own to stop for - the key is everything up to the first
        // unquoted ':', bracket included.
        expect:
        parse('m[1:]{v}:\n  k[2]: 5').get('m').get('k[2]').get('v').numberValue == 5L
    }

    void 'test a tab-delimited keyed tabular entry with two empty cells decodes, rather than being mistaken for no cells at all'() {
        // valuesText is a single tab here: a significant empty-cell marker
        // under the tab delimiter, not incidental trailing whitespace.
        expect:
        def entry = parse('m[1:\t]{a\tb}:\n  x:\t').get('m').get('x')
        entry.get('a').stringValue == ''
        entry.get('b').stringValue == ''
    }

    void 'test decoding accepts a non-conforming field name in a tabular header field list'() {
        // Same leniency applies to a field name in a tabular header's
        // field list - it doesn't need to match the encoder's own
        // unquoted-key grammar either.
        given:
        def tree = parse('items[2]{2key}:\n  1\n  2')

        expect:
        tree.get('items').values().toList()*.get('2key')*.numberValue == [1, 2]
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

    void 'test decoding unescapes a \\u escape'() {
        expect:
        parse('value: "\\u0041"').get('value').stringValue == 'A'
    }

    void 'test decoding unescapes a surrogate pair from two \\u escapes'() {
        given:
        // U+1F600 GRINNING FACE, encoded as its UTF-16 surrogate pair.
        def tree = parse('value: "\\uD83D\\uDE00"')

        expect:
        tree.get('value').stringValue.codePointAt(0) == 0x1F600
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

    void 'test a document indent size other than 2 is inferred and enforced consistently, not hardcoded'() {
        // The document's indent step is established from its first nesting
        // transition, then every later transition anywhere in the document
        // must match it - not a fixed default of 2.
        given:
        def tree = parse('a:\n    b: 1\nc:\n    d:\n        e: 1')

        expect:
        tree.get('a').get('b').numberValue == 1L
        tree.get('c').get('d').get('e').numberValue == 1L
    }

    void 'test inconsistent sibling indentation is a strict decode error'() {
        when:
        // "age" is indented one space deeper than "name", its sibling.
        parse('address:\n  name: Alice\n   age: 30')

        then:
        thrown(SerdeException)
    }

    void 'test a blank line between sibling object fields is harmless and silently skipped'() {
        // A blank line is only a strict-mode error inside a declared-count
        // array/keyed body (list items, tabular rows, keyed entry rows) -
        // not between object fields or other top-level constructs.
        given:
        def tree = parse('name: Alice\n\nage: 30')

        expect:
        tree.get('name').stringValue == 'Alice'
        tree.get('age').numberValue == 30L
    }

    void 'test a blank line right after an array body ends is harmless and silently skipped'() {
        given:
        def tree = parse('items[2]:\n  - a\n  - b\n\nname: Alice')

        expect:
        tree.get('items').values().toList()*.stringValue == ['a', 'b']
        tree.get('name').stringValue == 'Alice'
    }

    @Unroll
    void 'test a blank line between an array header and its first row/entry/item is harmless and silently skipped: #description'() {
        // A blank line is only rejected *between* two rows/entries/items -
        // nothing has started yet before the first one.
        expect:
        parse(text) != null

        where:
        description   | text
        'list item'   | 'items[1]:\n\n  - a'
        'tabular row' | 'items[1]{a}:\n\n  1'
        'entry row'   | 'envs[1:]{region}:\n\n  prod: us'
    }

    void 'test a list item whose first field is itself a tabular array, with a sibling field after it'() {
        // The first field's own body must validate one indent step in from
        // the item, not against the item's own indent directly - and the
        // sibling that follows is still found relative to the item's indent.
        given:
        def tree = parse('items[1]:\n  - users[2]{id,name}:\n      1,Ada\n      2,Bob\n    status: active')

        expect:
        tree.get('items').get(0).get('users').values().toList()*.get('name')*.stringValue == ['Ada', 'Bob']
        tree.get('items').get(0).get('status').stringValue == 'active'
    }

    void 'test a list item whose first field is itself a keyed-tabular header, with a sibling field after it'() {
        given:
        def tree = parse('items[2]:\n  - config[2:]{x}:\n      a: 1\n      b: 2\n    status: ok\n  - status: down')

        expect:
        tree.get('items').get(0).get('config').get('a').get('x').numberValue == 1L
        tree.get('items').get(0).get('config').get('b').get('x').numberValue == 2L
        tree.get('items').get(0).get('status').stringValue == 'ok'
        tree.get('items').get(1).get('status').stringValue == 'down'
    }

    void 'test a list item whose first field is a deeply nested plain object'() {
        given:
        def tree = parse('items[2]:\n  - properties:\n      state:\n        type: string\n  - id: 2')

        expect:
        tree.get('items').get(0).get('properties').get('state').get('type').stringValue == 'string'
        tree.get('items').get(1).get('id').numberValue == 2L
    }

    void 'test a list item whose first field is an array of arrays, with a sibling field after it'() {
        given:
        def tree = parse('items[1]:\n  - matrix[2]:\n      - [2]: 1,2\n      - [2]: 3,4\n    name: grid')

        expect:
        tree.get('items').get(0).get('matrix').get(0).values().toList()*.numberValue == [1L, 2L]
        tree.get('items').get(0).get('matrix').get(1).values().toList()*.numberValue == [3L, 4L]
        tree.get('items').get(0).get('name').stringValue == 'grid'
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
        description                                                   | text
        'inline array declares more values than given'                | 'tags[3]: a,b'
        'tabular header declares more rows than given'                | 'items[2]{a}:\n  1'
        'tabular row has the wrong number of cells'                   | 'items[2]{a,b}:\n  1,2\n  3'
        'duplicate field name in a field list'                        | 'items[2]{a,a}:\n  1,2\n  3,4'
        'malformed header missing a closing bracket'                  | 'tags[3: a,b,c'
        'unterminated quoted string'                                  | 'value: "unterminated'
        'duplicate object field key'                                  | 'name: Alice\nname: Bob'
        'duplicate keyed-tabular entry key'                           | 'envs[2:]{region}:\n  prod: us\n  prod: eu'
        'keyed tabular entry row with no cells'                       | 'm[1:]{v}:\n  a:'
        'blank line between list items'                               | 'items[2]:\n  - a\n\n  - b'
        'blank line between tabular rows'                             | 'items[2]{a}:\n  1\n\n  2'
        'blank line between keyed tabular entry rows'                 | 'envs[2:]{region}:\n  prod: us\n\n  dev: eu'
        'blank line between a list item\'s own fields'                | 'items[2]:\n  - a: 1\n\n    b: 2\n  - x'
        'blank line inside the last list item\'s fields'              | 'items[1]:\n  - a: 1\n\n    b: 2'
        'indentation step not a multiple of the document indent size' | 'a:\n  b: 1\nc:\n    d: 1'
        'indentation depth jump within a nested chain'                | 'a:\n  b:\n    c: 1\n    d:\n        e: 1'
        'keyless tabular header as a list item'                       | 'items[1]:\n  - [2]{x}:\n    1\n    2'
        'keyless keyed-tabular header as a list item'                 | 'items[1]:\n  - [1:]{v}:\n    a: 1'
        '\\u escape with a leading plus sign'                         | 'value: "\\u+041"'
        '\\u escape with a leading minus sign'                        | 'value: "\\u-041"'
        'lone high surrogate in a \\u escape'                         | 'value: "\\uD800"'
        'lone low surrogate in a \\u escape'                          | 'value: "\\uDC00"'
        'high surrogate not followed by a low surrogate'              | 'value: "\\uD800\\u0041"'
        'indented root content'                                       | '  name: Alice'
        'space before colon in unquoted key'                          | 'name : Alice'
        'space before array header bracket'                           | 'items [2]: 1,2'
        'space before colon in quoted key'                            | '"name" : Alice'
        'whitespace around unquoted field in list'                    | 'items[2]{ id, name}:\n  1, Alice\n  2, Bob'
        'huge declared tabular row count'                             | 'items[2000000000]{a}:\n  1'
        'huge declared list item count'                               | 'items[2000000000]:\n  - a'
    }

    private static LimitingStream.RemainingLimits limitsOf(int maximumNestingDepth) {
        LimitingStream.limitsFromConfiguration([getMaximumNestingDepth: { ->
            maximumNestingDepth
        }] as SerdeConfiguration)
    }

    private static JsonNode parseWithLimit(String text, int maximumNestingDepth) {
        new ToonDecoder().parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), limitsOf(maximumNestingDepth))
    }

    void 'test the nesting-depth guard rejects a chain of single-item nested lists at a small configured limit'() {
        given:
        int levels = 6
        def sb = new StringBuilder('[1]:\n')
        for (int i = 0; i < levels; i++) {
            sb.append('  ' * (i + 1)).append('- [1]:\n')
        }

        sb.append('  ' * (levels + 1)).append('- 1')
        def deeplyNestedList = sb.toString()

        expect:
        parse(deeplyNestedList) // fits comfortably under the default budget

        when:
        parseWithLimit(deeplyNestedList, 3)

        then:
        thrown(SerdeException)
    }

    void 'test an exponent too large for BigDecimal is a SerdeException, not a raw NumberFormatException'() {
        when:
        parse('value: 1e99999999999999999999')

        then:
        thrown(SerdeException)
    }

    void 'test invalid UTF-8 is rejected, not silently replaced with U+FFFD'() {
        given:
        // 0xC3 starts a 2-byte sequence but must be followed by a
        // continuation byte in 0x80-0xBF; 0x28 ('(') is not one.
        byte[] invalidUtf8 = [0x76, 0x61, 0x6c, 0x75, 0x65, 0x3a, 0x20, 0xC3, 0x28] as byte[]

        when:
        new ToonDecoder().parse(new ByteArrayInputStream(invalidUtf8))

        then:
        thrown(SerdeException)
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

    void 'test a trailing tab is not stripped, since tab is a legal delimiter'() {
        expect:
        // An intentionally-empty last cell in a tab-delimited row must
        // survive: stripping a trailing tab would silently drop it.
        parse('tags[3\t]: a\tb\t').get('tags').values().toList()*.stringValue == ['a', 'b', '']
    }

    void 'test a trailing tab after a comma-delimited header colon is insignificant, not a one-token inline array'() {
        expect:
        // Unlike the tab-delimited case above, a plain tab is not the
        // active delimiter here, so it carries no significance and must
        // not be mistaken for a single-token inline array body - the
        // header has no field list and no meaningful inline tail, so it
        // is list-form content on the following lines.
        parse('items[2]:\t\n  - a\n  - b').get('items').values().toList()*.stringValue == ['a', 'b']
    }

    void 'test a trailing tab on a bare root scalar is insignificant, not part of the value'() {
        expect:
        // No delimiter is in play for a lone root scalar, so the trailing
        // tab must not stop it from being recognized and parsed as a number.
        parse('42\t').numberValue == 42
    }

    void 'test a trailing tab after a bare list-item dash is insignificant, not a malformed item'() {
        expect:
        // "-\t" is still the empty-object shorthand: no delimiter is in
        // play for a bare dash marker either.
        parse('items[1]:\n  -\t').get('items').get(0) == JsonNode.createObjectNode([:])
    }

    void 'test an unterminated quoted scalar at the document root reports a line number'() {
        when:
        parse('"unterminated')

        then:
        SerdeException ex = thrown(SerdeException)
        ex.message.contains('at line 1')
    }

    void 'test an unterminated quoted scalar as a list item value reports a line number'() {
        when:
        parse('items[1]:\n  - "unterminated')

        then:
        SerdeException ex = thrown(SerdeException)
        ex.message.contains('at line 2')
    }

    void 'test a line-number annotation is not suppressed by user text that happens to contain "at line "'() {
        when:
        // The unterminated quoted value itself contains the literal
        // substring "at line ", which must not be mistaken for an
        // already-annotated message and cause the real line number (2)
        // to be silently dropped.
        parse('foo: 1\nvalue: "at line 99 nonsense')

        then:
        SerdeException ex = thrown(SerdeException)
        ex.message.contains('at line 2')
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
        'abc\t'      | 'abc\t'
        'abc  \t \t' | 'abc  \t \t'
        '  abc  \t'  | '  abc  \t'
        '   '        | ''
        '\t\t'       | '\t\t'
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

    void 'test parser diagnostics include line numbers in exception messages'() {
        when:
        parse('''name: Alice
age: 30
  broken_indent: true''')

        then:
        def e = thrown(SerdeException)
        e.message.contains('line 3')

        when:
        parse('''name: Alice
name: Bob''')

        then:
        def e2 = thrown(SerdeException)
        e2.message.contains('line 2')
        e2.message.contains("Duplicate key 'name'")

        when:
        parse('''items[2]:
  - first
  - second
  - third''')

        then:
        def e3 = thrown(SerdeException)
        e3.message.contains('line 4')

        when:
        parse('''items[2]{id,name}:
  1,Alice
  2''')

        then:
        def e4 = thrown(SerdeException)
        e4.message.contains('line 3')
    }
}
