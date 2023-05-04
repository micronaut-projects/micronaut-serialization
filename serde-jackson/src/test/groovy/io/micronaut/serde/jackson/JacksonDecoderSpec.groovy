package io.micronaut.serde.jackson

import com.fasterxml.jackson.core.JsonFactoryBuilder
import io.micronaut.serde.Decoder
import io.micronaut.serde.exceptions.SerdeException
import org.intellij.lang.annotations.Language
import spock.lang.Specification

class JacksonDecoderSpec extends Specification {
    private static Decoder createDecoder(@Language('json') String json) {
        return JacksonDecoder.create(new JsonFactoryBuilder().build().createParser(json))
    }

    def "unwrap arrays normal"() {
        expect:
        createDecoder('["a"]').decodeString() == 'a'
        createDecoder('[42]').decodeInt() == 42
        createDecoder('[42]').decodeDouble() == 42
        createDecoder('[42]').decodeBigInteger() == BigInteger.valueOf(42)
        createDecoder('[42]').decodeBigDecimal() == BigDecimal.valueOf(42)
        createDecoder('[true]').decodeBoolean()
    }

    def "unwrap arrays nested"() {
        when:
        createDecoder('[["a"]]').decodeString()

        then:
        thrown SerdeException
    }

    def "unwrapping places the cursor correctly"() {
        given:
        def decoder = createDecoder('[["a"],42]')
        def array = decoder.decodeArray()

        expect:
        array.decodeString() == 'a'
        array.decodeInt() == 42
        array.finishStructure()
    }

    def 'char reading'() {
        given:
        def decoder = createDecoder('["a",42]')
        def array = decoder.decodeArray()

        expect:
        array.decodeChar() == (char) 'a'
        array.decodeChar() == (char) '*'
    }

    def 'coercions'() {
        expect:
        createDecoder('42').decodeString() == '42'
        createDecoder('42.0').decodeString() == '42.0'
        createDecoder('42.00').decodeString() == '42.00'
        createDecoder('true').decodeString() == 'true'

        createDecoder('0.1').decodeBoolean()
        createDecoder('1').decodeBoolean()
        !createDecoder('0').decodeBoolean()
        !createDecoder('0.0').decodeBoolean()
        createDecoder('"true"').decodeBoolean()
        !createDecoder('"false"').decodeBoolean()
        !createDecoder('"foo"').decodeBoolean()

        createDecoder('true').decodeInt() == 1
        createDecoder('false').decodeInt() == 0

        createDecoder('true').decodeDouble() == 1
        createDecoder('false').decodeDouble() == 0

        createDecoder('true').decodeBigInteger() == BigInteger.ONE
        createDecoder('false').decodeBigInteger() == BigInteger.ZERO

        createDecoder('true').decodeBigDecimal() == BigDecimal.ONE
        createDecoder('false').decodeBigDecimal() == BigDecimal.ZERO
    }

    def 'buffering'() {
        expect:
        createDecoder('"a"').decodeBuffer().decodeString() == 'a'
        createDecoder('42').decodeBuffer().decodeInt() == 42
        createDecoder('42').decodeBuffer().decodeDouble() == 42
        createDecoder('42').decodeBuffer().decodeBigInteger() == BigInteger.valueOf(42)
        createDecoder('42').decodeBuffer().decodeBigDecimal() == BigDecimal.valueOf(42)
        createDecoder('true').decodeBuffer().decodeBoolean()

        when:
        def decoder = createDecoder('[{"foo":"bar"}, false]')
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
        createDecoder('{"f1": 42, "f2": "foo", "f3": true, "f4": [56, {"f5": "bar"}]}').decodeArbitrary() == [
                f1: 42,
                f2: 'foo',
                f3: true,
                f4: [56, [f5: 'bar']]
        ]
    }

    def "empty input"() {
        when:
        createDecoder('').decodeString()
        then:
        thrown EOFException
    }

    def "decode incompatible to string"() {
        when:
        createDecoder('{}').decodeStringNullable()
        then:
        thrown SerdeException
    }
}
