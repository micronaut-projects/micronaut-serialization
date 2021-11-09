package io.micronaut.serde.json.stream

import io.micronaut.json.tree.JsonNode
import io.micronaut.serde.JsonNodeDecoder
import jakarta.json.Json
import jakarta.json.stream.JsonParser
import org.intellij.lang.annotations.Language
import spock.lang.Specification

class JsonParserDecoderTest extends Specification {
    static JsonParser createParser(@Language('json') String s) {
        return Json.createParser(new StringReader(s))
    }
    
    def 'buffering'() {
        expect:
        new JsonParserDecoder(createParser('"a"')).decodeBuffer().decodeString() == 'a'
        new JsonParserDecoder(createParser('42')).decodeBuffer().decodeInt() == 42
        new JsonParserDecoder(createParser('42')).decodeBuffer().decodeDouble() == 42
        new JsonParserDecoder(createParser('42')).decodeBuffer().decodeBigInteger() == BigInteger.valueOf(42)
        new JsonParserDecoder(createParser('42')).decodeBuffer().decodeBigDecimal() == BigDecimal.valueOf(42)
        new JsonParserDecoder(createParser('true')).decodeBuffer().decodeBoolean()

        when:
        def decoder = new JsonParserDecoder(createParser('[{"foo":"bar"}, false]'))
        def array = decoder.decodeArray()
        def buffer = array.decodeBuffer()

        then:
        array.hasNextArrayValue()
        !array.decodeBoolean()
        !array.hasNextArrayValue()
        array.finishStructure()

        when:
        def object = buffer.decodeObject()

        then:
        object.decodeKey() == 'foo'
        object.decodeString() == 'bar'
        object.decodeKey() == null
        object.finishStructure()
    }

    def 'arbitrary decode'() {
        expect:
        new JsonParserDecoder(createParser('{"f1": 42, "f2": "foo", "f3": true, "f4": [56, {"f5": "bar"}]}')).decodeArbitrary() == [
                f1: 42,
                f2: 'foo',
                f3: true,
                f4: [56, [f5: 'bar']]
        ]
    }
}
