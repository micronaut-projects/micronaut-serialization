package io.micronaut.serde.yaml

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.serde.exceptions.InvalidFormatException
import io.micronaut.serde.exceptions.SerdeException
import io.micronaut.serde.yaml.data.Book
import io.micronaut.serde.yaml.data.Numbers
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import java.nio.charset.StandardCharsets

@MicronautTest
class YamlDecoderSpec extends Specification {

    @Inject
    YamlObjectMapper mapper

    def "root sequence is decoded"() {
        expect:
        mapper.readValue("- A\n- B\n", Argument.listOf(String)) == ["A", "B"]
        mapper.readValue("- title: One\n  pages: 1\n- title: Two\n  pages: 2\n", Argument.listOf(Book)) ==
                [new Book("One", 1), new Book("Two", 2)]
    }

    def "root scalar is decoded"() {
        expect:
        mapper.readValue("hello\n", String) == "hello"
        mapper.readValue("42\n", Integer) == 42
        mapper.readValue("true\n", Boolean)
        mapper.readValue("null\n", Argument.of(String)) == null
        mapper.readValue("--- |\n  line1\n  line2\n", String) == "line1\nline2\n"
    }

    def "explicit document markers and trailing comments are accepted"() {
        expect:
        mapper.readValue("---\ntitle: T\npages: 3\n...\n# done\n", Book) == new Book("T", 3)
    }

    def "empty input fails with a clear message"() {
        when:
        mapper.readValue(yaml, Book)

        then:
        def e = thrown(SerdeException)
        e.message.contains("No YAML document")

        where:
        yaml << ["", "# only a comment\n"]
    }

    def "unknown nested collections are skipped"() {
        expect:
        mapper.readValue('''
title: T
extra:
  nested:
    - 1
    - {a: b}
  other: [x, y]
pages: 7
more: [1, 2]
''', Book) == new Book("T", 7)
    }

    def "complex mapping keys are rejected"() {
        when:
        mapper.readValue("? [a, b]\n: value\n", Argument.mapOf(String, Object))

        then:
        def e = thrown(SerdeException)
        e.message.contains("Complex YAML mapping keys")
    }

    def "legacy booleans are read as booleans when configured"() {
        given:
        def context = ApplicationContext.run(['micronaut.serde.format.yaml.read-features.boolean-as-strings': false])
        def legacy = context.getBean(YamlObjectMapper)

        expect:
        legacy.readValue("a: yes\nb: Off\nc: y\n", Argument.mapOf(String, Object)) == [a: true, b: false, c: "y"]

        cleanup:
        context.close()
    }
    def "an alias without a matching anchor is rejected"() {
        when:
        mapper.readValue("a: *missing\n", Argument.mapOf(String, Object))

        then:
        def e = thrown(SerdeException)
        e.message.contains("*missing")
    }

    def "an alias inside a redefinition of an anchor name is rejected instead of resolving the older node"() {
        when:
        // *x names the &x it sits inside, not the [1] the name held before it
        mapper.readValue("a: &x [1]\nb: &x [*x]\n", Argument.mapOf(String, Object))

        then:
        def e = thrown(SerdeException)
        e.message.contains("the anchor is still open")
    }

    def "an alias may reuse a name a closed anchor already used"() {
        expect:
        mapper.readValue("a: &x [1]\nb: &x [2]\nc: *x\n", Argument.mapOf(String, Object)).c == [2]
    }

    def "a document that expands into too many events through aliases is rejected"() {
        given:
        // an anchored sequence repeated until the whole document blows past the expansion budget
        def yaml = new StringBuilder("anchor: &a [")
        1000.times { yaml.append("0,") }
        yaml.append("0]\nuse:\n")
        1100.times { yaml.append("  - *a\n") }

        when:
        mapper.readValue(yaml.toString(), Argument.mapOf(String, Object))

        then:
        def e = thrown(SerdeException)
        e.message.contains("expands into more than")
    }

    def "ordinary alias use stays well inside the expansion budget"() {
        given:
        def yaml = new StringBuilder("anchor: &a [1, 2, 3]\nuse:\n")
        500.times { yaml.append("  - *a\n") }

        when:
        def read = mapper.readValue(yaml.toString(), Argument.mapOf(String, Object))

        then:
        read.use.size() == 500
        read.use.every { it == [1, 2, 3] }
    }

    def "an alias to a node that is still open is rejected instead of recursing"() {
        when:
        mapper.readValue("&root\nfirst: 1\nself: *root\n", Argument.mapOf(String, Object))

        then:
        def e = thrown(SerdeException)
        e.message.contains("*root")
        e.message.contains("still open")
    }

    def "aliases and merges resolved inside an anchored node are replayed"() {
        when:
        def map = mapper.readValue('''
base: &base
  values: &vals [1, 2]
derived: &derived
  <<: *base
  list: *vals
copy: *derived
''', Argument.mapOf(String, Object))

        then:
        map.copy == [values: [1, 2], list: [1, 2]]
        map.derived == map.copy
    }

    def "a merge key accepts a sequence of mappings"() {
        when:
        def map = mapper.readValue('''
a: &a {x: 1, y: 1}
b: &b {y: 2, z: 2}
merged:
  <<: [*a, *b]
  w: 0
''', Argument.mapOf(String, Object))

        then:
        map.merged == [z: 2, y: 1, x: 1, w: 0]
        map.merged.y == 1
    }

    def "keys defined by the mapping override the merged ones"() {
        when:
        def map = mapper.readValue('''
base: &base
  a: 1
  b: 2
derived:
  <<: *base
  b: 3
  c: 4
''', Argument.mapOf(String, Object))

        then:
        map.derived == [a: 1, b: 3, c: 4]
        map.base == [a: 1, b: 2]
    }

    def "a key defined before the merge key still overrides the merged one"() {
        when:
        def map = mapper.readValue('''
base: &base
  a: 1
  b: 2
derived:
  b: 3
  <<: *base
''', Argument.mapOf(String, Object))

        then:
        map.derived == [a: 1, b: 3]
    }

    def "an anchor on a merged value can be aliased"() {
        when:
        def map = mapper.readValue('''
derived:
  <<: &defaults {a: 1}
  b: 2
other: *defaults
''', Argument.mapOf(String, Object))

        then:
        map.derived == [a: 1, b: 2]
        map.other == [a: 1]
    }

    def "a merge key in a mapping with many entries is spliced"() {
        given:
        def yaml = new StringBuilder("base: &base\n  seed: 0\nbig:\n  <<: *base\n")
        6000.times { yaml.append("  k").append(it).append(": ").append(it).append("\n") }

        when:
        def map = mapper.readValue(yaml.toString(), Argument.mapOf(String, Object))

        then:
        map.big.size() == 6001
        map.big.seed == 0
        map.big.k5999 == 5999
    }

    def "an earlier mapping of a merged sequence overrides a later one"() {
        when:
        def map = mapper.readValue('''
first: &first {x: 1, y: 1}
second: &second {y: 2, z: 2}
merged:
  <<: [*first, *second]
  z: 3
''', Argument.mapOf(String, Object))

        then:
        map.merged == [x: 1, y: 1, z: 3]
    }

    def "a merge key with an inline mapping is spliced"() {
        expect:
        mapper.readValue("m:\n  <<: {a: 1}\n  b: 2\n", Argument.mapOf(String, Object)).m == [a: 1, b: 2]
    }

    def "a quoted merge key is a regular key"() {
        expect:
        mapper.readValue('"<<": literal\n', Argument.mapOf(String, Object)) == ["<<": "literal"]
    }

    def "a merge key with a scalar value is rejected"() {
        when:
        mapper.readValue("m:\n  <<: nope\n", Argument.mapOf(String, Object))

        then:
        def e = thrown(SerdeException)
        e.message.contains("merge key")
    }
    def "core schema integers are parsed"() {
        expect:
        mapper.readValue("i: $yaml\n", Numbers).i() == expected

        where:
        yaml   || expected
        "12"   || 12
        "+12"  || 12
        "-12"  || -12
        "0x1F" || 31
        "0o17" || 15
        "0"    || 0
    }

    def "core schema floats are parsed"() {
        expect:
        mapper.readValue("d: $yaml\n", Numbers).d() == expected

        where:
        yaml    || expected
        "1.5"   || 1.5d
        ".5"    || 0.5d
        "1e3"   || 1000d
        "-2.5E-1" || -0.25d
        ".inf"  || Double.POSITIVE_INFINITY
        "-.Inf" || Double.NEGATIVE_INFINITY
        "+.INF" || Double.POSITIVE_INFINITY
    }

    def "nan is parsed"() {
        expect:
        Double.isNaN(mapper.readValue("d: .nan\n", Numbers).d())
        Float.isNaN(mapper.readValue("f: .NaN\n", Numbers).f())
    }

    def "big numbers keep their precision"() {
        when:
        def numbers = mapper.readValue('''
l: 9223372036854775807
bigInteger: 92233720368547758070
bigDecimal: 0.10000000000000000555111512312578
''', Numbers)

        then:
        numbers.l() == Long.MAX_VALUE
        numbers.bigInteger() == new BigInteger("92233720368547758070")
        numbers.bigDecimal() == new BigDecimal("0.10000000000000000555111512312578")
    }

    def "untyped numbers use the narrowest type"() {
        when:
        def map = mapper.readValue('''
i: 1
l: 4294967296
big: 92233720368547758070
d: 1.5
hex: 0xFF
''', Argument.mapOf(String, Object))

        then:
        map.i instanceof Integer
        map.l instanceof Long
        map.big instanceof BigInteger
        map.d instanceof Double
        map.hex == 255
    }

    def "a long that does not fit fails as an invalid format"() {
        when:
        mapper.readValue("l: 92233720368547758070\n", Numbers)

        then:
        def e = thrown(InvalidFormatException)
        e.originalValue == "92233720368547758070"
    }
    def "malformed yaml is reported as a serde exception with a location"() {
        when:
        mapper.readValue("title: [unclosed\npages: 1\n", Book)

        then:
        def e = thrown(SerdeException)
        e.message.contains("Invalid YAML input")
        e.message.contains("line")
    }

    def "type errors carry the location of the scalar"() {
        when:
        mapper.readValue("title: T\npages: many\n", Book)

        then:
        def e = thrown(SerdeException)
        e.message.contains("line 2")
    }
    def "input is read in the encoding its byte order mark declares"() {
        expect:
        mapper.readValue(encode("title: T\npages: 3\n", bom, charset), Book) == new Book("T", 3)

        where:
        bom                        | charset
        []                         | StandardCharsets.UTF_8
        [0xEF, 0xBB, 0xBF]         | StandardCharsets.UTF_8
        []                         | StandardCharsets.UTF_16    // this one writes the mark itself
        [0xFF, 0xFE]               | StandardCharsets.UTF_16LE
        [0xFE, 0xFF]               | StandardCharsets.UTF_16BE
    }

    private static byte[] encode(String yaml, List<Integer> bom, java.nio.charset.Charset charset) {
        def bytes = new ByteArrayOutputStream()
        bom.each { bytes.write(it) }
        bytes.write(yaml.getBytes(charset))
        bytes.toByteArray()
    }

    def "a number whose exponent would expand without bound is rejected"() {
        when:
        mapper.readValue("bigInteger: 1e200000000\n", Numbers)

        then:
        def e = thrown(InvalidFormatException)
        e.message.contains("exponent beyond the supported range")
    }

    def "the same exponent is read as a double, nothing is expanded for it"() {
        expect:
        mapper.readValue("d: 1e200000000\n", Numbers).d() == Double.POSITIVE_INFINITY
    }

    def "input larger than the code point limit is rejected"() {
        given:
        def context = ApplicationContext.run(['micronaut.serde.format.yaml.read-features.code-point-limit': 20])
        def limited = context.getBean(YamlObjectMapper)

        when:
        limited.readValue("title: " + ("x" * 32) + "\npages: 1\n", Book)

        then:
        thrown(SerdeException)

        when:
        def book = limited.readValue("title: T\npages: 1", Book)

        then:
        book == new Book("T", 1)

        cleanup:
        context.close()
    }
}
