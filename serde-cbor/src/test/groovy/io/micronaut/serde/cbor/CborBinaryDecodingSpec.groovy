package io.micronaut.serde.cbor

import io.micronaut.core.type.Argument
import io.micronaut.json.tree.JsonNode
import io.micronaut.serde.cbor.data.BinaryBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

/**
 * CBOR byte strings arrive as {@code VALUE_EMBEDDED_OBJECT}, not as a scalar token, so every
 * decoding path (not just {@code decodeBinary}) has to understand them.
 */
@MicronautTest
class CborBinaryDecodingSpec extends Specification {

    private static final byte[] PAYLOAD = [0x00, 0xFF, 0x10, 0x7F] as byte[]

    @Inject
    CborObjectMapper cborMapper

    private byte[] wire() {
        cborMapper.writeValueAsBytes(new BinaryBean("n", PAYLOAD))
    }

    def "byte string decodes into an Object-typed value"() {
        when:
        Map<String, Object> decoded = cborMapper.readValue(wire(), Argument.of(Object))

        then:
        Arrays.equals((byte[]) decoded.data, PAYLOAD)
    }

    def "byte string decodes into a Map with Object values"() {
        when:
        Map<String, Object> decoded = cborMapper.readValue(wire(), Argument.mapOf(String, Object))

        then:
        Arrays.equals((byte[]) decoded.data, PAYLOAD)
    }

    def "byte string decodes into a tree as base64, matching JsonNodeEncoder"() {
        when:
        JsonNode tree = cborMapper.readValue(wire(), Argument.of(JsonNode))

        then:
        tree.get("data").getStringValue() == Base64.encoder.encodeToString(PAYLOAD)

        and: "the tree still deserializes back to the original bytes"
        cborMapper.readValueFromTree(tree, BinaryBean).data() == PAYLOAD
    }

    def "byte string inside a nested structure"() {
        given:
        byte[] wire = cborMapper.writeValueAsBytes([outer: [inner: PAYLOAD]])

        when:
        Map<String, Object> decoded = cborMapper.readValue(wire, Argument.of(Object))

        then:
        Arrays.equals((byte[]) decoded.outer.inner, PAYLOAD)
    }
}
