package io.micronaut.serde

import io.micronaut.json.tree.JsonNode
import io.micronaut.serde.support.util.JsonNodeDecoder
import spock.lang.Specification

class JsonNodeDecoderSpec extends Specification {
    private static JsonNodeDecoder create(JsonNode jsonNode) {
        return JsonNodeDecoder.create(jsonNode, LimitingStream.DEFAULT_LIMITS)
    }

    def 'scalar decode'() {
        expect:
        create(JsonNode.createNumberNode(42)).decodeByte() == (byte) 42
        create(JsonNode.createNumberNode(42)).decodeShort() == (short) 42
        create(JsonNode.createNumberNode(42)).decodeInt() == 42
        create(JsonNode.createNumberNode(42)).decodeLong() == 42L
        create(JsonNode.createNumberNode(42)).decodeFloat() == 42.0F
        create(JsonNode.createNumberNode(42)).decodeDouble() == 42.0D
        create(JsonNode.createNumberNode(42)).decodeBigInteger() == BigInteger.valueOf(42)
        create(JsonNode.createNumberNode(42)).decodeBigDecimal() == BigDecimal.valueOf(42)

        create(JsonNode.createStringNode('foo')).decodeString() == 'foo'
        create(JsonNode.createBooleanNode(true)).decodeBoolean()
        create(JsonNode.nullNode()).decodeNull()
    }

    def 'array decode'() {
        given:
        def decoder = create(JsonNode.createArrayNode([
                JsonNode.createNumberNode(42),
                JsonNode.createStringNode('foo'),
                JsonNode.createBooleanNode(true),
                JsonNode.createArrayNode([]),
        ]))

        when:
        def arrayDecoder = decoder.decodeArray()

        then:
        arrayDecoder.hasNextArrayValue()
        arrayDecoder.decodeInt() == 42
        arrayDecoder.hasNextArrayValue()
        arrayDecoder.decodeString() == 'foo'
        arrayDecoder.hasNextArrayValue()
        arrayDecoder.decodeBoolean()
        arrayDecoder.hasNextArrayValue()

        when:
        def childArrayDecoder = arrayDecoder.decodeArray()

        then:
        !childArrayDecoder.hasNextArrayValue()
        childArrayDecoder.finishStructure()

        !arrayDecoder.hasNextArrayValue()
        arrayDecoder.finishStructure()
    }

    def 'object decode'() {
        given:
        def decoder = create(JsonNode.createObjectNode([
                f1: JsonNode.createNumberNode(42),
                f2: JsonNode.createStringNode('foo'),
                f3: JsonNode.createBooleanNode(true),
                f4: JsonNode.createArrayNode([]),
        ]))

        when:
        def objectDecoder = decoder.decodeObject()

        then:
        objectDecoder.decodeKey() == 'f1'
        objectDecoder.decodeInt() == 42
        objectDecoder.decodeKey() == 'f2'
        objectDecoder.decodeString() == 'foo'
        objectDecoder.decodeKey() == 'f3'
        objectDecoder.decodeBoolean()
        objectDecoder.decodeKey() == 'f4'

        when:
        def childArrayDecoder = objectDecoder.decodeArray()

        then:
        !childArrayDecoder.hasNextArrayValue()
        childArrayDecoder.finishStructure()

        objectDecoder.decodeKey() == null
        objectDecoder.finishStructure()
    }

    def 'object decode lookahead'() {

        when:
        def decoder = create(JsonNode.createObjectNode([
                f1: JsonNode.createNumberNode(42),
                f2: JsonNode.createStringNode('foo'),
                f3: JsonNode.createBooleanNode(true),
                f4: JsonNode.createArrayNode([JsonNode.createNumberNode(42), JsonNode.createStringNode('foo')]),
        ])) as LookaheadDecoder

        then:
        decoder.lookahead() == LookaheadDecoder.TokenType.START_OBJECT
        def objectDecoder = decoder.decodeObject()
        objectDecoder.lookahead() == LookaheadDecoder.TokenType.KEY
        objectDecoder.decodeKey() == 'f1'
        objectDecoder.lookahead() == LookaheadDecoder.TokenType.NUMBER
        objectDecoder.decodeInt() == 42
        objectDecoder.lookahead() == LookaheadDecoder.TokenType.KEY
        objectDecoder.decodeKey() == 'f2'
        objectDecoder.lookahead() == LookaheadDecoder.TokenType.STRING
        objectDecoder.decodeString() == 'foo'
        objectDecoder.lookahead() == LookaheadDecoder.TokenType.KEY
        objectDecoder.decodeKey() == 'f3'
        objectDecoder.lookahead() == LookaheadDecoder.TokenType.BOOLEAN
        objectDecoder.decodeBoolean()
        objectDecoder.lookahead() == LookaheadDecoder.TokenType.KEY
        objectDecoder.decodeKey() == 'f4'
        objectDecoder.lookahead() == LookaheadDecoder.TokenType.START_ARRAY
        def arrayDecoder = objectDecoder.decodeArray()
        arrayDecoder.lookahead() == LookaheadDecoder.TokenType.NUMBER
        arrayDecoder.skipValue()
        arrayDecoder.hasNextArrayValue()
        arrayDecoder.lookahead() == LookaheadDecoder.TokenType.STRING
        arrayDecoder.hasNextArrayValue()
        arrayDecoder.skipValue()
        !arrayDecoder.hasNextArrayValue()
        arrayDecoder.lookahead() == LookaheadDecoder.TokenType.END_ARRAY
        objectDecoder.lookahead() == LookaheadDecoder.TokenType.END_OBJECT
        decoder.lookahead() == LookaheadDecoder.TokenType.END_OBJECT
    }

    def 'array decode lookahead'() {

        when:
        def decoder = create(JsonNode.createArrayNode(
                [JsonNode.createNumberNode(42), JsonNode.createStringNode('foo')]
        )) as LookaheadDecoder

        then:
        decoder.lookahead() == LookaheadDecoder.TokenType.START_ARRAY
        def arrayDecoder = decoder.decodeArray()
        arrayDecoder.lookahead() == LookaheadDecoder.TokenType.NUMBER
        arrayDecoder.skipValue()
        arrayDecoder.hasNextArrayValue()
        arrayDecoder.lookahead() == LookaheadDecoder.TokenType.STRING
        arrayDecoder.hasNextArrayValue()
        arrayDecoder.skipValue()
        !arrayDecoder.hasNextArrayValue()
        arrayDecoder.lookahead() == LookaheadDecoder.TokenType.END_ARRAY
        decoder.lookahead() == LookaheadDecoder.TokenType.END_ARRAY
    }

    def 'arbitrary decode'() {
        given:
        def decoder = create(JsonNode.createObjectNode([
                f1: JsonNode.createNumberNode(42),
                f2: JsonNode.createStringNode('foo'),
                f3: JsonNode.createBooleanNode(true),
                f4: JsonNode.createArrayNode([
                        JsonNode.createNumberNode(56),
                        JsonNode.createObjectNode([
                                f5: JsonNode.createStringNode('bar')
                        ])
                ]),
        ]))

        expect:
        decoder.decodeArbitrary() == [
                f1: 42,
                f2: 'foo',
                f3: true,
                f4: [56, [f5: 'bar']]
        ]
    }
}
