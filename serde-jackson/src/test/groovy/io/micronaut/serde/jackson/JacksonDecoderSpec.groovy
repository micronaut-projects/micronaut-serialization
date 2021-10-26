package io.micronaut.serde.jackson

import com.fasterxml.jackson.core.JsonFactoryBuilder
import io.micronaut.serde.exceptions.SerdeException
import spock.lang.Specification

class JacksonDecoderSpec extends Specification {
    def "unwrap arrays normal"() {
        given:
        def factory = new JsonFactoryBuilder().build()

        expect:
        JacksonDecoder.create(factory.createParser('["a"]')).decodeString() == 'a'
        JacksonDecoder.create(factory.createParser('[42]')).decodeInt() == 42
        JacksonDecoder.create(factory.createParser('[42]')).decodeDouble() == 42
        JacksonDecoder.create(factory.createParser('[42]')).decodeBigInteger() == BigInteger.valueOf(42)
        JacksonDecoder.create(factory.createParser('[42]')).decodeBigDecimal() == BigDecimal.valueOf(42)
        JacksonDecoder.create(factory.createParser('[true]')).decodeBoolean()
    }

    def "unwrap arrays nested"() {
        given:
        def factory = new JsonFactoryBuilder().build()

        when:
        JacksonDecoder.create(factory.createParser('[["a"]]')).decodeString()

        then:
        thrown SerdeException
    }

    def "unwrapping places the cursor correctly"() {
        given:
        def factory = new JsonFactoryBuilder().build()
        def decoder = JacksonDecoder.create(factory.createParser('[["a"],42]'))
        def array = decoder.decodeArray()

        expect:
        array.decodeString() == 'a'
        array.decodeInt() == 42
        array.finishStructure()
    }

    def 'char reading'() {
        given:
        def factory = new JsonFactoryBuilder().build()
        def decoder = JacksonDecoder.create(factory.createParser('["a",42]'))
        def array = decoder.decodeArray()

        expect:
        array.decodeChar() == (char) 'a'
        array.decodeChar() == (char) '*'
    }

    def 'buffering'() {
        given:
        def factory = new JsonFactoryBuilder().build()

        expect:
        JacksonDecoder.create(factory.createParser('"a"')).decodeBuffer().decodeString() == 'a'
        JacksonDecoder.create(factory.createParser('42')).decodeBuffer().decodeInt() == 42
        JacksonDecoder.create(factory.createParser('42')).decodeBuffer().decodeDouble() == 42
        JacksonDecoder.create(factory.createParser('42')).decodeBuffer().decodeBigInteger() == BigInteger.valueOf(42)
        JacksonDecoder.create(factory.createParser('42')).decodeBuffer().decodeBigDecimal() == BigDecimal.valueOf(42)
        JacksonDecoder.create(factory.createParser('true')).decodeBuffer().decodeBoolean()

        when:
        def decoder = JacksonDecoder.create(factory.createParser('[{"foo":"bar"}, false]'))
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
}
