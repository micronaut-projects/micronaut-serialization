package io.micronaut.serde.cbor

import io.micronaut.serde.cbor.data.BinaryBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class CborByteArraySpec extends Specification {

    @Inject
    CborObjectMapper cborMapper

    def "byte arrays round-trip as binary not base64 text"() {
        given:
        byte[] payload = [0x00, 0xFF, 0x10, 0x7F] as byte[]
        def bean = new BinaryBean("bin", payload)

        when:
        byte[] cbor = cborMapper.writeValueAsBytes(bean)
        def read = cborMapper.readValue(cbor, BinaryBean)

        then:
        read.name() == "bin"
        Arrays.equals(read.data(), payload)
        // Ensure we did not encode as a base64 text string somewhere in the payload
        !new String(cbor, "ISO-8859-1").contains("AP8Qfw==")
        // CBOR byte string major type 2 (high bits 010)
        cbor.any { (it & 0xE0) == 0x40 }
    }

    def "empty byte array"() {
        given:
        def bean = new BinaryBean("empty", new byte[0])

        when:
        def read = cborMapper.readValue(cborMapper.writeValueAsBytes(bean), BinaryBean)

        then:
        read.data().length == 0
    }
}
