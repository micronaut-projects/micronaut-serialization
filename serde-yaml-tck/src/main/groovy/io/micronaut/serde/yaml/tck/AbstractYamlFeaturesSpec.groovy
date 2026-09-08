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
import spock.lang.Ignore
import spock.lang.Specification

/**
 * Validates the configurable read and write features.
 */
abstract class AbstractYamlFeaturesSpec extends Specification implements YamlSpec {

    private static final String READ = 'micronaut.serde.format.yaml.read-features.'
    private static final String WRITE = 'micronaut.serde.format.yaml.write-features.'

    void "an empty plain scalar is read as null by default"() {
        expect:
        readYaml(yaml, Argument.mapOf(String, Object)).key == null

        where:
        yaml << ['key:\n', 'key:   \n']
    }

    void "an empty plain scalar is read as an empty string when configured"() {
        expect:
        readYamlWithProperties([(READ + 'empty-string-as-null'): false], yaml, Argument.mapOf(String, Object)).key == ''

        where:
        yaml << ['key:\n', 'key:   \n']
    }

    // NOTE: Jackson 3.0 behavior differs from 2.x due to changes in the
    // underlying "snakeyaml-engine" behavior
    void "reserved YAML words are read as their core schema type by default"() {
        expect:
        readYaml('key: ' + value + '\n', Argument.mapOf(String, Object)).key == expected

        where:
        value   || expected
        "null"  || null
        "Null"  || null
        "NULL"  || null
        "true"  || true
        "True"  || true
        "TRUE"  || true
        "false" || false
        "False" || false
        "FALSE" || false
        "yes"   || "yes"
        "Yes"   || "Yes"
        "YES"   || "YES"
        "no"    || "no"
        "No"    || "No"
        "NO"    || "NO"
        "y"     || "y"
        "Y"     || "Y"
        "n"     || "n"
        "N"     || "N"
        "on"    || "on"
        "On"    || "On"
        "ON"    || "ON"
        "off"   || "off"
        "Off"   || "Off"
        "OFF"   || "OFF"
    }

    void "quotes are minimized by default"() {
        expect:
        writeYamlWithProperties([(WRITE + 'minimize-quotes'): true], [key: value]) in expected

        where:
        value    || expected
        // safe plain scalars, they stay unquoted while quotes are minimized
        'a:b'    || ['key: a:b\n']
        'f:off'  || ['key: f:off\n']
        'a#b'    || ['key: a#b\n']
        'a# b'   || ['key: a# b\n']
        132      || ['key: 132\n']
        '"132"'  || ['key: \'"132"\'\n']
        // these carry YAML indicators and are quoted whatever the configuration
        'a: b'   || ['key: "a: b"\n']
        '::'     || ['key: "::"\n']
        '#'      || ['key: "#"\n']
        '#a'     || ['key: "#a"\n']
        'a[b'    || ['key: "a[b"\n']
        'a]b'    || ['key: "a]b"\n']
        'a{b'    || ['key: "a{b"\n']
        'a}b'    || ['key: "a}b"\n']
        'a,b'    || ['key: "a,b"\n']
    }

    void "minimized quotes preserve boolean-like strings"() {
        expect:
        writeYamlWithProperties([(WRITE + 'minimize-quotes'): true], [
                trueString  : "true",
                falseString : "false",
                text        : "something else",
                booleanValue: true
        ]) == '''trueString: "true"
falseString: "false"
text: something else
booleanValue: true
'''
    }

    void "quotes are written around every string when minimize quotes is disabled"() {
        expect:
        writeYamlWithProperties([(WRITE + 'minimize-quotes'): false], [value: "something else", booleanValue: true]) ==
                '''value: "something else"
booleanValue: true
'''
    }

    void "non-finite numbers use the configured notation"() {
        expect:
        writeYamlWithProperties([(WRITE + 'use-yaml-nonfinite-notation'): useYamlNonfiniteNotation], [key: value]) ==
                "key: " + expected + "\n"

        where:
        useYamlNonfiniteNotation | value                    || expected
        true                     | Double.NaN               || ".nan"
        true                     | Double.POSITIVE_INFINITY || ".inf"
        true                     | Double.NEGATIVE_INFINITY || "-.inf"
        true                     | Float.NaN                || ".nan"
        false                    | Double.NaN               || "NaN"
        false                    | Double.POSITIVE_INFINITY || "Infinity"
        false                    | Double.NEGATIVE_INFINITY || "-Infinity"
        false                    | Float.NEGATIVE_INFINITY  || "-Infinity"
    }

    void "long keys are written in explicit form unless they are allowed"() {
        given:
        def key = 'a' * 129

        expect:
        writeYamlWithProperties([(WRITE + 'allow-long-keys'): allowLongKeys], [(key): 'value']) ==
                expectedPrefix + key + expectedSuffix

        where:
        allowLongKeys || expectedPrefix | expectedSuffix
        false         || '? '           | '\n: value\n'
        true          || ''             | ': value\n'
    }

    void "sequence indicators are indented as configured"() {
        expect:
        writeYamlWithProperties([
                (WRITE + 'indent-arrays')               : indentArrays,
                (WRITE + 'indent-arrays-with-indicator'): indentArraysWithIndicator
        ], [values: ['A', 'B']]) == expected

        where:
        indentArrays | indentArraysWithIndicator || expected
        false        | false                     || "values:\n- A\n- B\n"
        true         | false                     || "values:\n - A\n - B\n"
        false        | true                      || "values:\n  - A\n  - B\n"
        true         | true                      || "values:\n  - A\n  - B\n"
    }

    void "canonical output is written when configured"() {
        expect:
        writeYamlWithProperties([(WRITE + 'canonical-output'): canonicalOutput], [key: 'value']) == expected

        where:
        canonicalOutput || expected
        false           || "key: value\n"
        true            || "---\n{\n  ? \"key\"\n  : \"value\",\n}\n"
    }

    void "long quoted strings are split across lines unless split lines is disabled"() {
        expect:
        writeYamlWithProperties([
                (WRITE + 'minimize-quotes'): false,
                (WRITE + 'split-lines')    : splitLines
        ], ["1234567890 1234567890 1234567890 1234567890 1234567890 1234567890 1234567890 1234567890 1234567890"]).trim() == expected

        where:
        splitLines || expected
        true       || "- \"1234567890 1234567890 1234567890 1234567890 1234567890 1234567890 1234567890 1234567890\\\n  \\ 1234567890\""
        false      || "- \"1234567890 1234567890 1234567890 1234567890 1234567890 1234567890 1234567890 1234567890 1234567890\""
    }

    @Ignore("Trailing whitespace in literal blocks was fixed after the Jackson 3 release the parity suite runs against")
    void "multiline strings use literal block style when configured"() {
        expect:
        writeYamlWithProperties([(WRITE + 'literal-block-style'): true], [text: "Hello\nWorld "]).trim() ==
                "text: |-\n  Hello\n  World "
    }

    void "collections are written in the configured style"() {
        expect:
        writeYamlWithProperties([(WRITE + 'write-style'): writeStyle], [values: ['A', 'B'], counts: [one: 1, two: 2]]) == expected

        where:
        writeStyle || expected
        'BLOCK'    || "values:\n- A\n- B\ncounts:\n  one: 1\n  two: 2\n"
        'FLOW'     || "{values: [A, B], counts: {one: 1, two: 2}}\n"
    }

    void "document markers are written when configured"() {
        expect:
        writeYamlWithProperties([
                (WRITE + 'explicit-start'): explicitStart,
                (WRITE + 'explicit-end')  : explicitEnd
        ], [value: 'A']) == expected

        where:
        explicitStart | explicitEnd || expected
        true          | true        || "---\nvalue: A\n...\n"
        true          | false       || "---\nvalue: A\n"
        false         | true        || "value: A\n...\n"
    }

    void "the configured indent is used for nested mappings"() {
        expect:
        writeYamlWithProperties([(WRITE + 'indent'): 4], [counts: [one: 1, two: 2]]) ==
                "counts:\n    one: 1\n    two: 2\n"
    }
}
