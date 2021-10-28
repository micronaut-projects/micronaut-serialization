package io.micronaut.serde

import io.micronaut.json.tree.JsonNode
import spock.lang.Specification

import java.util.function.Consumer

class JsonNodeEncoderSpec extends Specification {
    private def encode(Consumer<Encoder> fn) {
        def encoder = JsonNodeEncoder.create()
        fn.accept(encoder)
        return encoder.getCompletedValue()
    }

    def 'scalar encode'() {
        expect:
        encode { it.encodeByte((byte) 42) } == JsonNode.createNumberNode(42)
        encode { it.encodeShort((short) 42) } == JsonNode.createNumberNode(42)
        encode { it.encodeInt(42) } == JsonNode.createNumberNode(42)
        encode { it.encodeLong(42) } == JsonNode.createNumberNode(42L)
        encode { it.encodeFloat(42.5) } == JsonNode.createNumberNode(42.5F)
        encode { it.encodeDouble(42.5) } == JsonNode.createNumberNode(42.5D)
        encode { it.encodeBigInteger(BigInteger.valueOf(42)) } == JsonNode.createNumberNode(BigInteger.valueOf(42))
        encode { it.encodeBigDecimal(BigDecimal.valueOf(42.5)) } == JsonNode.createNumberNode(BigDecimal.valueOf(42.5))
        encode { it.encodeString('foo') } == JsonNode.createStringNode('foo')
        encode { it.encodeBoolean(true) } == JsonNode.createBooleanNode(true)
        encode { it.encodeNull() } == JsonNode.nullNode()
    }

    def 'object encode'() {
        expect:
        encode {
            def obj = it.encodeObject()
            obj.encodeKey('foo')
            obj.encodeString('bar')
            obj.encodeKey('baz')
            obj.encodeInt(42)
            obj.finishStructure()
        } == JsonNode.createObjectNode([
                foo: JsonNode.createStringNode('bar'),
                baz: JsonNode.createNumberNode(42),
        ])
    }

    def 'array encode'() {
        expect:
        encode {
            def arr = it.encodeArray()
            arr.encodeString('bar')
            arr.encodeInt(42)
            arr.finishStructure()
        } == JsonNode.createArrayNode([
                JsonNode.createStringNode('bar'),
                JsonNode.createNumberNode(42),
        ])
    }
}
