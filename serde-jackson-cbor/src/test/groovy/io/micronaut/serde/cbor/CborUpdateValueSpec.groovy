package io.micronaut.serde.cbor

import io.micronaut.core.type.Argument
import io.micronaut.json.tree.JsonNode
import io.micronaut.serde.cbor.data.MutableBook
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class CborUpdateValueSpec extends Specification {

    @Inject
    CborObjectMapper cborMapper

    private static MutableBook book() {
        new MutableBook(title: "The Stand", pages: 454)
    }

    def "update from CBOR bytes applies only the present properties"() {
        given:
        def target = book()
        byte[] overrides = cborMapper.writeValueAsBytes(Argument.of(JsonNode), JsonNode.from([pages: 1138]))

        when:
        cborMapper.updateValue(target, Argument.of(MutableBook), overrides)

        then:
        target.title == "The Stand"
        target.pages == 1138
    }

    def "update from an input stream"() {
        given:
        def target = book()
        byte[] overrides = cborMapper.writeValueAsBytes(Argument.of(JsonNode), JsonNode.from([title: "IT"]))

        when:
        cborMapper.updateValue(target, Argument.of(MutableBook), new ByteArrayInputStream(overrides))

        then:
        target.title == "IT"
        target.pages == 454
    }

    def "update from a tree"() {
        given:
        def target = book()

        when:
        cborMapper.updateValueFromTree(target, JsonNode.from([title: "IT", pages: 1138]))

        then:
        target.title == "IT"
        target.pages == 1138
    }

    def "update from another object"() {
        given:
        def target = book()
        def source = new MutableBook(title: "IT", pages: 1138)

        when:
        cborMapper.updateValue(target, Argument.of(MutableBook), source)

        then:
        target.title == "IT"
        target.pages == 1138
    }

    def "a null tree leaves the value untouched"() {
        given:
        def target = book()

        when:
        cborMapper.updateValueFromTree(target, JsonNode.nullNode())

        then:
        target.title == "The Stand"
        target.pages == 454
    }

    def "null overrides leave the value untouched"() {
        given:
        def target = book()

        when:
        cborMapper.updateValue(target, Argument.of(MutableBook), (Object) null)

        then:
        target.title == "The Stand"
        target.pages == 454
    }
}
