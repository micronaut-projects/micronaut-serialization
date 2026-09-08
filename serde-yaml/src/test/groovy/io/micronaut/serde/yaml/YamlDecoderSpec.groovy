package io.micronaut.serde.yaml

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.serde.exceptions.SerdeException
import io.micronaut.serde.yaml.data.Book
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

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
}
