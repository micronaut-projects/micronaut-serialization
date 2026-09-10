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
import io.micronaut.serde.toon.SerdeToonConfiguration
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Direct unit tests of {@link ToonEncoder} against hand-built {@link JsonNode}
 * fixtures, independent of bean serialization.
 */
class ToonEncoderSpec extends Specification {

    private static String toText(ToonEncoder writer, JsonNode tree) {
        def out = new ByteArrayOutputStream()
        writer.write(out, tree)
        return out.toString('UTF-8')
    }

    private static ToonEncoder writer(SerdeToonConfiguration.Delimiter delimiter = SerdeToonConfiguration.Delimiter.COMMA) {
        def config = new SerdeToonConfiguration()
        config.delimiter = delimiter
        new ToonEncoder(config)
    }

    void 'test a flat object with primitive fields'() {
        given:
        def tree = JsonNode.createObjectNode([
                name: JsonNode.createStringNode('Alice'),
                age : JsonNode.createNumberNode(30)
        ])

        expect:
        toText(writer(), tree) == '''name: Alice
age: 30'''
    }

    void 'test a nested object'() {
        given:
        def tree = JsonNode.createObjectNode([
                address: JsonNode.createObjectNode([
                        city: JsonNode.createStringNode('Springfield'),
                        zip : JsonNode.createStringNode('12345')
                ])
        ])

        expect:
        // "12345" is a numeric-looking string, so it must stay quoted -
        // otherwise it would decode back as the number 12345, not a string.
        toText(writer(), tree) == '''address:
  city: Springfield
  zip: "12345"'''
    }

    void 'test an inline primitive array'() {
        given:
        def tree = JsonNode.createObjectNode([
                tags: JsonNode.createArrayNode([
                        JsonNode.createStringNode('a'),
                        JsonNode.createStringNode('b'),
                        JsonNode.createStringNode('c')
                ])
        ])

        expect:
        toText(writer(), tree) == 'tags[3]: a,b,c'
    }

    void 'test a uniform array of records encodes as tabular form'() {
        // Modeled on the weather-forecast example from the TOON specification/docs.
        given:
        def tree = JsonNode.createObjectNode([
                location: JsonNode.createStringNode('Springfield'),
                forecast: JsonNode.createArrayNode([
                        JsonNode.createObjectNode([
                                day      : JsonNode.createStringNode('Mon'),
                                temp     : JsonNode.createNumberNode(68),
                                condition: JsonNode.createStringNode('Sunny')
                        ]),
                        JsonNode.createObjectNode([
                                day      : JsonNode.createStringNode('Tue'),
                                temp     : JsonNode.createNumberNode(72),
                                condition: JsonNode.createStringNode('Cloudy')
                        ])
                ])
        ])

        expect:
        toText(writer(), tree) == '''location: Springfield
forecast[2]{day,temp,condition}:
  Mon,68,Sunny
  Tue,72,Cloudy'''
    }

    void 'test a uniform nested-object column folds into the tabular header'() {
        given:
        def tree = JsonNode.createObjectNode([
                readings: JsonNode.createArrayNode([
                        JsonNode.createObjectNode([
                                day : JsonNode.createStringNode('Mon'),
                                temp: JsonNode.createObjectNode([min: JsonNode.createNumberNode(60), max: JsonNode.createNumberNode(70)])
                        ]),
                        JsonNode.createObjectNode([
                                day : JsonNode.createStringNode('Tue'),
                                temp: JsonNode.createObjectNode([min: JsonNode.createNumberNode(65), max: JsonNode.createNumberNode(75)])
                        ])
                ])
        ])

        expect:
        toText(writer(), tree) == '''readings[2]{day,temp{min,max}}:
  Mon,60,70
  Tue,65,75'''
    }

    void 'test a map of uniform records encodes as keyed tabular form'() {
        given:
        def tree = JsonNode.createObjectNode([
                envs: JsonNode.createObjectNode([
                        prod: JsonNode.createObjectNode([region: JsonNode.createStringNode('us'), replicas: JsonNode.createNumberNode(3)]),
                        dev : JsonNode.createObjectNode([region: JsonNode.createStringNode('eu'), replicas: JsonNode.createNumberNode(1)])
                ])
        ])

        expect:
        toText(writer(), tree) == '''envs[2:]{region,replicas}:
  prod: us,3
  dev: eu,1'''
    }

    void 'test a non-uniform array falls back to list form'() {
        given:
        def tree = JsonNode.createObjectNode([
                items: JsonNode.createArrayNode([
                        JsonNode.createStringNode('text'),
                        JsonNode.createObjectNode([id: JsonNode.createNumberNode(1)])
                ])
        ])

        expect:
        toText(writer(), tree) == '''items[2]:
  - text
  - id: 1'''
    }

    void 'test an array of arrays encodes each nested array on a hyphen line'() {
        given:
        def tree = JsonNode.createObjectNode([
                matrix: JsonNode.createArrayNode([
                        JsonNode.createArrayNode([JsonNode.createNumberNode(1), JsonNode.createNumberNode(2)]),
                        JsonNode.createArrayNode([JsonNode.createNumberNode(3), JsonNode.createNumberNode(4)])
                ])
        ])

        expect:
        toText(writer(), tree) == '''matrix[2]:
  - [2]: 1,2
  - [2]: 3,4'''
    }

    void 'test an empty array as a list item encodes as a zero-length header, not "- []"'() {
        given:
        // The spec requires "- [0<delim?>]:" for an empty inner array;
        // encoders must not emit "- []" even though decoders accept it.
        def tree = JsonNode.createObjectNode([
                items: JsonNode.createArrayNode([
                        JsonNode.createArrayNode([]),
                        JsonNode.createStringNode('text')
                ])
        ])

        expect:
        toText(writer(), tree) == '''items[2]:
  - [0]:
  - text'''
    }

    void 'test an empty array and an empty object'() {
        given:
        def tree = JsonNode.createObjectNode([
                tags: JsonNode.createArrayNode([]),
                meta: JsonNode.createObjectNode([:])
        ])

        expect:
        toText(writer(), tree) == '''tags: []
meta:'''
    }

    void 'test a root array has a keyless header'() {
        given:
        def tree = JsonNode.createArrayNode([JsonNode.createStringNode('a'), JsonNode.createStringNode('b')])

        expect:
        toText(writer(), tree) == '[2]: a,b'
    }

    @Unroll
    void 'test a root primitive: #description'() {
        expect:
        toText(writer(), tree) == expected

        where:
        description | tree                             | expected
        'string'    | JsonNode.createStringNode('hi')  | 'hi'
        'number'    | JsonNode.createNumberNode(42)    | '42'
        'boolean'   | JsonNode.createBooleanNode(true) | 'true'
        'null'      | JsonNode.nullNode()              | 'null'
    }

    void 'test an empty root object encodes as an empty document'() {
        expect:
        toText(writer(), JsonNode.createObjectNode([:])) == ''
    }

    @Unroll
    void 'test string values are quoted when required: #description'() {
        given:
        def tree = JsonNode.createObjectNode([value: JsonNode.createStringNode(input)])

        expect:
        toText(writer(), tree) == "value: ${expected}"

        where:
        description              | input      | expected
        'plain string'           | 'hello'    | 'hello'
        'looks like a boolean'   | 'true'     | '"true"'
        'looks like null'        | 'null'     | '"null"'
        'looks like a number'    | '123'      | '"123"'
        'starts with a hyphen'   | '-5apples' | '"-5apples"'
        'starts with a hash'     | '#comment' | '"#comment"'
        'contains the delimiter' | 'a,b'      | '"a,b"'
        'empty string'           | ''         | '""'
        'leading whitespace'     | ' padded'  | '" padded"'
    }

    void 'test string quoting depends on the configured delimiter'() {
        given:
        def tree = JsonNode.createObjectNode([value: JsonNode.createStringNode('a|b')])

        expect:
        toText(writer(SerdeToonConfiguration.Delimiter.COMMA), tree) == 'value: a|b'
        toText(writer(SerdeToonConfiguration.Delimiter.PIPE), tree) == 'value: "a|b"'
    }

    @Unroll
    void 'test number canonical formatting: #description'() {
        given:
        def tree = JsonNode.createObjectNode([value: numberNode])

        expect:
        toText(writer(), tree) == "value: ${expected}"

        where:
        description               | numberNode                                                            | expected
        'integer'                 | JsonNode.createNumberNode(42)                                         | '42'
        'negative integer'        | JsonNode.createNumberNode(-7)                                         | '-7'
        'decimal with fraction'   | JsonNode.createNumberNode(10.5d)                                      | '10.5'
        'decimal with trailing 0' | JsonNode.createNumberNode(new BigDecimal('10.50'))                    | '10.5'
        'whole double'            | JsonNode.createNumberNode(10.0d)                                      | '10'
        'NaN'                     | JsonNode.createNumberNode(Double.NaN)                                 | 'null'
        'positive infinity'       | JsonNode.createNumberNode(Double.POSITIVE_INFINITY)                   | 'null'
        'very large number'       | JsonNode.createNumberNode(new BigDecimal('100000000000000000000000')) | '1e+23'
        'very small number'       | JsonNode.createNumberNode(new BigDecimal('0.0000000001'))             | '1e-10'
    }

    void 'test tab and pipe delimiters change the header symbol and cell separator'() {
        given:
        def tree = JsonNode.createObjectNode([
                tags: JsonNode.createArrayNode([JsonNode.createStringNode('a'), JsonNode.createStringNode('b')])
        ])

        expect:
        toText(writer(SerdeToonConfiguration.Delimiter.TAB), tree) == "tags[2\t]: a\tb"
        toText(writer(SerdeToonConfiguration.Delimiter.PIPE), tree) == 'tags[2|]: a|b'
    }
}
