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

import java.math.BigDecimal
import java.math.BigInteger

/**
 * Validates scalar type conversions and quoting rules across TOON implementations.
 */
abstract class AbstractToonScalarSpec extends Specification implements ToonSpec {

    void "a plain scalar is decoded to appropriate types"() {
        when:
        def read = readToon("text: hello\nflag: true\nnumber: 42\ndecimal: 1.5\n", ScalarsBean)

        then:
        read.text() == "hello"
        read.flag()
        read.number() == 42
        read.decimal() == 1.5d
    }

    void "missing scalar fields decode to null, not a default primitive value"() {
        when:
        def read = readToon("text: hello\n", ScalarsBean)

        then:
        read.text() == "hello"
        read.flag() == null
        read.number() == null
        read.decimal() == null
        read.nothing() == null
    }

    void "untyped mapping resolves scalars to standard Java types"() {
        when:
        def read = readToon('''text: hello
flag: true
number: 42
decimal: 1.5
nothing: null
''', Argument.mapOf(String, Object))

        then:
        read.text instanceof String
        read.text == "hello"
        read.flag instanceof Boolean
        read.flag == true
        read.number instanceof Number
        ((Number) read.number).intValue() == 42
        read.decimal instanceof Number
        ((Number) read.decimal).doubleValue() == 1.5d
        read.nothing == null
    }

    void "a quoted scalar that looks like another type stays a string"() {
        when:
        def read = readToon('a: "true"\nb: "42"\nc: "001"\nd: "null"\n', Argument.mapOf(String, Object))

        then:
        read.a == "true"
        read.b == "42"
        read.c == "001"
        read.d == "null"
    }

    void "booleans are decoded and encoded canonically"() {
        expect:
        readToon("flag: " + value + "\n", ScalarsBean).flag() == expected
        writeToon([flag: expected]).trim() == "flag: " + value

        where:
        value   || expected
        "true"  || true
        "false" || false
    }

    void "numeric types in NumbersBean round trip correctly"() {
        given:
        def bean = new NumbersBean(
                123,
                9876543210L,
                3.14159d,
                2.718f,
                new BigInteger("12345678901234567890"),
                new BigDecimal("12345.67890")
        )

        when:
        def encoded = writeToon(bean)
        def decoded = readToon(encoded, NumbersBean)

        then:
        decoded.i() == 123
        decoded.l() == 9876543210L
        // TOON's canonical decimal formatting round-trips doubles/floats
        // exactly, so an equality check here (rather than a tolerance) can
        // still catch a real precision regression.
        decoded.d() == 3.14159d
        decoded.f() == 2.718f
        decoded.bigInteger() == new BigInteger("12345678901234567890")
        decoded.bigDecimal().compareTo(new BigDecimal("12345.67890")) == 0
    }

    void "strings requiring quoting are quoted on serialization"() {
        expect:
        writeToon([a: value]).trim() == 'a: "' + escapedExpected + '"'

        where:
        value          || escapedExpected
        "true"         || "true"
        "false"        || "false"
        "null"         || "null"
        "0123"         || "0123"
        "-42"          || "-42"
        "hello, world" || "hello, world"
        "foo: bar"     || "foo: bar"
        "line1\nline2" || "line1\\nline2"
        " leading"     || " leading"
        "trailing "    || "trailing "
        ""             || ""
    }

    void "safe unquoted identifiers are written without quotes"() {
        expect:
        writeToon([name: "active_user_123"]).trim() == "name: active_user_123"
    }
}
