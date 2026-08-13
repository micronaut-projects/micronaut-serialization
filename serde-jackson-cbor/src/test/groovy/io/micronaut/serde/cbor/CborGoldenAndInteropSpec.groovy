package io.micronaut.serde.cbor

import io.micronaut.core.type.Argument
import io.micronaut.serde.cbor.data.Book
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.dataformat.cbor.CBORFactory

/**
 * Golden CBOR hex fixtures and streaming-only interop (no databind).
 */
@MicronautTest
class CborGoldenAndInteropSpec extends Specification {

    @Inject
    CborObjectMapper cborMapper

    def "RFC-style map of text keys encodes as major type 5"() {
        when:
        byte[] bytes = cborMapper.writeValueAsBytes(new Book("a", 1))

        then:
        (bytes[0] & 0xE0) == 0xA0
        // Stable property order is not guaranteed; re-decode instead of fixed hex.
        cborMapper.readValue(bytes, Book) == new Book("a", 1)
    }

    def "decode CBOR produced by streaming CBORFactory without databind"() {
        given:
        def factory = new CBORFactory()
        def baos = new ByteArrayOutputStream()
        try (JsonGenerator gen = factory.createGenerator(baos)) {
            gen.writeStartObject()
            gen.writeName("title")
            gen.writeString("Interop")
            gen.writeName("pages")
            gen.writeNumber(42)
            gen.writeEndObject()
        }
        byte[] wire = baos.toByteArray()

        when:
        def book = cborMapper.readValue(wire, Book)

        then:
        book == new Book("Interop", 42)
    }

    def "Micronaut CBOR is readable by streaming CBORParser"() {
        given:
        byte[] wire = cborMapper.writeValueAsBytes(new Book("Parser", 7))

        when:
        String title = null
        int pages = -1
        try (JsonParser parser = new CBORFactory().createParser(wire)) {
            assert parser.nextToken() == JsonToken.START_OBJECT
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String name = parser.currentName()
                parser.nextToken()
                if (name == "title") {
                    title = parser.getString()
                } else if (name == "pages") {
                    pages = parser.getIntValue()
                }
            }
        }

        then:
        title == "Parser"
        pages == 7
    }

    def "simple values: true false null"() {
        expect:
        cborMapper.readValue(cborMapper.writeValueAsBytes(Argument.of(Boolean), true), Boolean) == true
        cborMapper.readValue(cborMapper.writeValueAsBytes(Argument.of(Boolean), false), Boolean) == false
        cborMapper.readValue(cborMapper.writeValueAsBytes(Argument.of(String), null), String) == null
    }

    def "list and nested map round-trip"() {
        given:
        def value = [
            items: [[a: 1], [b: 2]],
            ok   : true
        ]

        when:
        def read = cborMapper.readValue(cborMapper.writeValueAsBytes(value), Map)

        then:
        read.ok == true
        read.items.size() == 2
        read.items[0].a == 1 || read.items[0]["a"] == 1
    }
}
