package io.micronaut.serde.cbor

import io.micronaut.serde.cbor.data.Point
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class CborInputStreamSpec extends Specification {

    @Inject
    CborObjectMapper cborMapper

    def "read and write via streams"() {
        given:
        def bean = new Point(3, 4)
        def baos = new ByteArrayOutputStream()

        when:
        cborMapper.writeValue(baos, bean)
        def read = cborMapper.readValue(new ByteArrayInputStream(baos.toByteArray()), Point)

        then:
        read == bean
    }
}
