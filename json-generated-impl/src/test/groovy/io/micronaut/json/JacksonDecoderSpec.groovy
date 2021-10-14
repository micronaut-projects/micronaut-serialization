package io.micronaut.json


import com.fasterxml.jackson.core.JsonFactoryBuilder
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
        thrown DeserializationException
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
}
