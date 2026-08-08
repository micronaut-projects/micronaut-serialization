package io.micronaut.serde.cbor

import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.json.tree.JsonNode
import io.micronaut.serde.cbor.data.Book
import io.micronaut.serde.cbor.data.Color
import io.micronaut.serde.cbor.data.EnumBean
import io.micronaut.serde.cbor.data.NestedBean
import io.micronaut.serde.jackson.JacksonJsonMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class CborRoundTripSpec extends Specification {

    @Inject
    CborObjectMapper cborMapper

    @Inject
    JsonMapper jsonMapper

    def "primary JsonMapper remains Jackson when CBOR is present"() {
        expect:
        jsonMapper instanceof JacksonJsonMapper
        cborMapper instanceof CborObjectMapper
        !(jsonMapper instanceof CborObjectMapper)
    }

    def "round-trip simple record"() {
        given:
        def book = new Book("The Stand", 454)

        when:
        byte[] bytes = cborMapper.writeValueAsBytes(book)
        def read = cborMapper.readValue(bytes, Book)

        then:
        bytes != null
        bytes.length > 0
        // CBOR map major type 5 → high 3 bits 101 (0xA0 range), not ASCII '{'
        (bytes[0] & 0xE0) == 0xA0
        read == book
    }

    def "round-trip nested collections and maps"() {
        given:
        def bean = new NestedBean(
            "id-1",
            ["a", "b"],
            [x: 1, y: 2],
            new NestedBean.Child("c", true)
        )

        when:
        def read = cborMapper.readValue(cborMapper.writeValueAsBytes(bean), NestedBean)

        then:
        read == bean
    }

    def "round-trip via Argument"() {
        given:
        def book = new Book("IT", 1138)

        when:
        byte[] bytes = cborMapper.writeValueAsBytes(Argument.of(Book), book)
        def read = cborMapper.readValue(bytes, Argument.of(Book))

        then:
        read == book
    }

    def "null root writes and reads as null"() {
        when:
        byte[] bytes = cborMapper.writeValueAsBytes(null)
        def read = cborMapper.readValue(bytes, Book)

        then:
        read == null
    }

    def "JsonNode tree conversion"() {
        given:
        def book = new Book("Wizard", 10)

        when:
        JsonNode tree = cborMapper.writeValueToTree(book)
        def fromTree = cborMapper.readValueFromTree(tree, Book)
        def viaBytes = cborMapper.readValue(cborMapper.writeValueAsBytes(book), Book)

        then:
        fromTree == book
        viaBytes == book
        tree.get("title").getStringValue() == "Wizard"
        tree.get("pages").getIntValue() == 10
    }

    def "list of beans"() {
        given:
        def list = [new Book("A", 1), new Book("B", 2)]

        when:
        byte[] bytes = cborMapper.writeValueAsBytes(Argument.listOf(Book), list)
        def read = cborMapper.readValue(bytes, Argument.listOf(Book))

        then:
        read == list
    }

    def "enum property"() {
        given:
        def bean = new EnumBean(Color.GREEN)

        when:
        def read = cborMapper.readValue(cborMapper.writeValueAsBytes(bean), EnumBean)

        then:
        read == bean
    }
}
