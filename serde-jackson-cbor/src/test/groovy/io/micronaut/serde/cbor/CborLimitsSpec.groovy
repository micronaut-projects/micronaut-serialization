package io.micronaut.serde.cbor

import io.micronaut.context.annotation.Property
import io.micronaut.serde.exceptions.SerdeException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification
import tools.jackson.core.JsonGenerator
import tools.jackson.dataformat.cbor.CBORFactory

@Property(name = "micronaut.serde.maximum-nesting-depth", value = "3")
@MicronautTest
class CborLimitsSpec extends Specification {

    @Inject
    CborObjectMapper cborMapper

    def "deep nesting exceeds configured maximum depth on write"() {
        given:
        // depth: root object + nested object + nested object + nested object (> 3)
        def deep = [a: [b: [c: [d: 1]]]]

        when:
        cborMapper.writeValueAsBytes(deep)

        then:
        thrown(SerdeException)
    }

    def "deep nesting exceeds configured maximum depth on read"() {
        given: "CBOR written without limits, so the depth check has to happen while reading"
        def baos = new ByteArrayOutputStream()
        try (JsonGenerator gen = new CBORFactory().createGenerator(baos)) {
            4.times {
                gen.writeStartObject()
                gen.writeName("a")
            }
            gen.writeNumber(1)
            4.times { gen.writeEndObject() }
        }

        when:
        cborMapper.readValue(baos.toByteArray(), Map)

        then:
        thrown(SerdeException)
    }

    def "shallow nesting succeeds"() {
        given:
        def shallow = [a: [b: 1]]

        when:
        def bytes = cborMapper.writeValueAsBytes(shallow)
        def read = cborMapper.readValue(bytes, Map)

        then:
        read.a.b == 1
    }
}
