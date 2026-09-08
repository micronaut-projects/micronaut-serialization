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
}
