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
 * Validates how YAML scalars are typed when read and quoted when written.
 */
abstract class AbstractYamlScalarSpec extends Specification implements YamlSpec {

    void "a plain scalar is typed by the core schema"() {
        when:
        def read = readYaml("text: hello\nflag: true\nnumber: 42\ndecimal: 1.5\n", ScalarsBean)

        then:
        read.text() == "hello"
        read.flag()
        read.number() == 42
        read.decimal() == 1.5d
    }

    void "an untyped mapping resolves scalars to their core schema types"() {
        when:
        def read = readYaml('''
text: hello
flag: true
number: 42
decimal: 1.5
nothing: null
''', Argument.mapOf(String, Object))

        then:
        read.text instanceof String
        read.flag instanceof Boolean
        read.number instanceof Integer
        read.decimal instanceof Double
        read.nothing == null
    }

    void "a quoted scalar that looks like another type stays a string"() {
        when:
        def read = readYaml("a: 'true'\nb: \"42\"\nc: '001'\nd: 'null'\n", Argument.mapOf(String, Object))

        then:
        read.a == "true"
        read.b == "42"
        read.c == "001"
        read.d == "null"
    }

    void "canonical booleans are read as booleans"() {
        expect:
        readYaml("flag: " + value + "\n", ScalarsBean).flag() == expected

        where:
        value   || expected
        "true"  || true
        "True"  || true
        "TRUE"  || true
        "false" || false
        "False" || false
        "FALSE" || false
    }

    void "every null form is read as null"() {
        expect:
        readYaml("a: " + value + "\n", Argument.mapOf(String, Object)).a == null

        where:
        value << ["null", "Null", "NULL", "~"]
    }

    void "a string holding a reserved word is quoted when written"() {
        expect:
        writeYaml([a: value]) == 'a: "' + value + '"\n'

        where:
        value << ["true", "false", "null", "yes", "no", "on", "off", "y", "n", "~"]
    }

    void "a plain string is written without quotes"() {
        expect:
        writeYaml([a: "hello world"]) == "a: hello world\n"
    }

    void "scalars round trip through a string that looks like another type"() {
        given:
        def bean = new ScalarsBean("true", false, 1, 2.5d, null)

        when:
        def read = readYaml(writeYaml(bean), ScalarsBean)

        then:
        read.text() == "true"
        !read.flag()
        read.number() == 1
        read.decimal() == 2.5d
        read.nothing() == null
    }

    void "core schema integers are read"() {
        expect:
        readYaml("i: " + value + "\n", NumbersBean).i() == expected

        where:
        value  || expected
        "12"   || 12
        "+12"  || 12
        "-12"  || -12
        "0"    || 0
        "0x1F" || 31
        "0o17" || 15
    }

    void "core schema floats are read"() {
        expect:
        readYaml("d: " + value + "\n", NumbersBean).d() == expected

        where:
        value     || expected
        "1.5"     || 1.5d
        "-2.5E-1" || -0.25d
        "1e3"     || 1000d
        ".inf"    || Double.POSITIVE_INFINITY
        "-.inf"   || Double.NEGATIVE_INFINITY
    }

    void "not a number is read"() {
        expect:
        Double.isNaN(readYaml("d: .nan\n", NumbersBean).d())
    }

    void "large numbers keep their precision"() {
        when:
        def read = readYaml('''
l: 9223372036854775807
bigInteger: 92233720368547758070
bigDecimal: 0.10000000000000000555111512312578
''', NumbersBean)

        then:
        read.l() == Long.MAX_VALUE
        read.bigInteger() == new BigInteger("92233720368547758070")
        read.bigDecimal() == new BigDecimal("0.10000000000000000555111512312578")
    }

    void "an untyped number uses the narrowest type that holds it"() {
        when:
        def read = readYaml("i: 1\nl: 4294967296\nbig: 92233720368547758070\nd: 1.5\n", Argument.mapOf(String, Object))

        then:
        read.i instanceof Integer
        read.l instanceof Long
        read.big instanceof BigInteger
        read.d instanceof Double
    }

    void "numbers round trip"() {
        given:
        def bean = new NumbersBean(1, 2L, 3.5d, 4.5f, new BigInteger("92233720368547758070"), new BigDecimal("1.25"))

        when:
        def read = readYaml(writeYaml(bean), NumbersBean)

        then:
        read.i() == 1
        read.l() == 2L
        read.d() == 3.5d
        read.f() == 4.5f
        read.bigInteger() == new BigInteger("92233720368547758070")
        read.bigDecimal() == new BigDecimal("1.25")
    }

    void "a multiline block scalar is read"() {
        expect:
        readYaml("text: |\n  line1\n  line2\n", ScalarsBean).text() == "line1\nline2\n"
        readYaml("text: |-\n  line1\n  line2\n", ScalarsBean).text() == "line1\nline2"
        readYaml("text: >-\n  line1\n  line2\n", ScalarsBean).text() == "line1 line2"
    }

    void "a multiline string round trips"() {
        expect:
        readYaml(writeYaml([a: "line1\nline2"]), Argument.mapOf(String, String)).a == "line1\nline2"
    }
}
