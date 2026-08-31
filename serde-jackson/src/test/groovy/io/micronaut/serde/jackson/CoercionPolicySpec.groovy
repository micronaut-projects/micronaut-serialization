package io.micronaut.serde.jackson

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.micronaut.context.ApplicationContext
import io.micronaut.serde.Decoder
import io.micronaut.serde.LimitingStream
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.serde.config.CoercionPolicy
import io.micronaut.serde.config.DeserializationConfiguration
import io.micronaut.serde.exceptions.SerdeException
import org.intellij.lang.annotations.Language
import spock.lang.Specification
import spock.lang.Unroll
import tools.jackson.core.json.JsonFactoryBuilder

class CoercionPolicySpec extends Specification {

    private static Decoder createDecoder(@Language('json') String json, CoercionPolicy policy) {
        return JacksonDecoder.create(new JsonFactoryBuilder().build().createParser(json), LimitingStream.DEFAULT_LIMITS, policy)
    }

    @Unroll
    def 'strict rejects #json read as #method'() {
        when:
        createDecoder(json, CoercionPolicy.STRICT)."$method"()

        then:
        thrown SerdeException

        when: 'the same input is lenient'
        def lenient = createDecoder(json, CoercionPolicy.LENIENT)."$method"()

        then:
        lenient == coerced

        where:
        json      | method            || coerced
        '42.5'    | 'decodeInt'       || 42
        '42.5'    | 'decodeLong'      || 42L
        '42.5'    | 'decodeByte'      || (byte) 42
        '42.5'    | 'decodeShort'     || (short) 42
        '42.5'    | 'decodeBigInteger'|| BigInteger.valueOf(42)
        '"42"'    | 'decodeInt'       || 42
        '"42"'    | 'decodeLong'      || 42L
        '"42.5"'  | 'decodeDouble'    || 42.5d
        '"42.5"'  | 'decodeBigDecimal'|| new BigDecimal('42.5')
        'true'    | 'decodeInt'       || 1
        'true'    | 'decodeDouble'    || 1d
        'true'    | 'decodeBigInteger'|| BigInteger.ONE
        '1'       | 'decodeBoolean'   || true
        '0.1'     | 'decodeBoolean'   || true
        '"true"'  | 'decodeBoolean'   || true
        '1234'    | 'decodeString'    || '1234'
        'true'    | 'decodeString'    || 'true'
        '[42]'    | 'decodeInt'       || 42
        '["a"]'   | 'decodeString'    || 'a'
        '[true]'  | 'decodeBoolean'   || true
    }

    def 'values of the right shape are untouched by the strict policy'() {
        expect:
        createDecoder('42', CoercionPolicy.STRICT).decodeInt() == 42
        createDecoder('42', CoercionPolicy.STRICT).decodeLong() == 42L
        createDecoder('42', CoercionPolicy.STRICT).decodeDouble() == 42d
        createDecoder('42.5', CoercionPolicy.STRICT).decodeDouble() == 42.5d
        createDecoder('42.5', CoercionPolicy.STRICT).decodeBigDecimal() == new BigDecimal('42.5')
        createDecoder('"a"', CoercionPolicy.STRICT).decodeString() == 'a'
        createDecoder('"a"', CoercionPolicy.STRICT).decodeChar() == (char) 'a'
        createDecoder('true', CoercionPolicy.STRICT).decodeBoolean()
        createDecoder('null', CoercionPolicy.STRICT).decodeStringNullable() == null
        createDecoder('null', CoercionPolicy.STRICT).decodeIntNullable() == null
    }

    def 'individual coercions can be allowed on top of the strict mode'() {
        given:
        def ctx = ApplicationContext.run([
                'micronaut.serde.deserialization.coercion-mode'          : 'STRICT',
                'micronaut.serde.deserialization.accept-string-as-number': true
        ])
        def policy = CoercionPolicy.fromConfiguration(ctx.getBean(DeserializationConfiguration))

        expect:
        createDecoder('"42"', policy).decodeInt() == 42

        when:
        createDecoder('42.5', policy).decodeInt()

        then:
        thrown SerdeException

        cleanup:
        ctx.close()
    }

    def 'the buffered decoder applies the same policy'() {
        expect: 'decodeX() and decodeBuffer().decodeX() agree, as Decoder#decodeBuffer documents'
        createDecoder('42.5', CoercionPolicy.LENIENT).decodeBuffer().decodeInt() == 42

        when:
        createDecoder('42.5', CoercionPolicy.STRICT).decodeBuffer().decodeInt()

        then:
        thrown SerdeException

        when:
        createDecoder('[42]', CoercionPolicy.STRICT).decodeBuffer().decodeInt()

        then:
        thrown SerdeException

        and: 'the lenient direction agrees as well'
        createDecoder('"42"', CoercionPolicy.LENIENT).decodeBuffer().decodeInt() == 42
        createDecoder('true', CoercionPolicy.LENIENT).decodeBuffer().decodeInt() == 1
        createDecoder('1234', CoercionPolicy.LENIENT).decodeBuffer().decodeString() == '1234'
        createDecoder('1', CoercionPolicy.LENIENT).decodeBuffer().decodeBoolean()
        createDecoder('"42.5"', CoercionPolicy.LENIENT).decodeBuffer().decodeDouble() == 42.5d
    }

    @Unroll
    def 'char reads agree between the streaming and buffered decoders for #json (#policyName)'() {
        given:
        def streaming = { -> createDecoder(json, policy).decodeChar() }
        def buffered = { -> createDecoder(json, policy).decodeBuffer().decodeChar() }

        expect:
        outcome(streaming) == outcome(buffered)
        outcome(streaming) == expected

        where:
        json   | policyName | policy                  || expected
        '"a"'  | 'lenient'  | CoercionPolicy.LENIENT  || 'a' as char
        '"a"'  | 'strict'   | CoercionPolicy.STRICT   || 'a' as char
        '"42"' | 'lenient'  | CoercionPolicy.LENIENT  || 'threw'
        '"42"' | 'strict'   | CoercionPolicy.STRICT   || 'threw'
        '""'   | 'lenient'  | CoercionPolicy.LENIENT  || 'threw'
        '""'   | 'strict'   | CoercionPolicy.STRICT   || 'threw'
        '42'   | 'lenient'  | CoercionPolicy.LENIENT  || 42 as char
        '42'   | 'strict'   | CoercionPolicy.STRICT   || 42 as char
        '42.5' | 'lenient'  | CoercionPolicy.LENIENT  || 42 as char
        '42.5' | 'strict'   | CoercionPolicy.STRICT   || 'threw'
        'true' | 'lenient'  | CoercionPolicy.LENIENT  || 1 as char
        'true' | 'strict'   | CoercionPolicy.STRICT   || 'threw'
    }

    private static Object outcome(Closure<?> read) {
        try {
            return read.call()
        } catch (IOException ignored) {
            return 'threw'
        }
    }

    def 'a buffered property is validated like an inline one'() {
        given:
        def ctx = ApplicationContext.run(['micronaut.serde.deserialization.accept-float-as-int': false])
        def mapper = ctx.getBean(ObjectMapper)

        when: 'read inline'
        mapper.readValue('{"type":"sub","number":42.5}', Base)

        then:
        thrown SerdeException

        when: 'the discriminator comes last, so the property is buffered'
        mapper.readValue('{"number":42.5,"type":"sub"}', Base)

        then:
        thrown SerdeException

        cleanup:
        ctx.close()
    }

    def 'buffered and inline properties agree under the default configuration'() {
        given:
        def ctx = ApplicationContext.run()
        def mapper = ctx.getBean(ObjectMapper)

        expect: 'the discriminator first, so the property is read inline'
        mapper.readValue('{"type":"sub","number":"42"}', Base).number == 42
        mapper.readValue('{"type":"sub","number":42.5}', Base).number == 42
        mapper.readValue('{"type":"sub","number":true}', Base).number == 1
        mapper.readValue('{"type":"sub","text":1234}', Base).text == '1234'

        and: 'the discriminator last, so the property is buffered'
        mapper.readValue('{"number":"42","type":"sub"}', Base).number == 42
        mapper.readValue('{"number":42.5,"type":"sub"}', Base).number == 42
        mapper.readValue('{"number":true,"type":"sub"}', Base).number == 1
        mapper.readValue('{"text":1234,"type":"sub"}', Base).text == '1234'

        cleanup:
        ctx.close()
    }

    def 'strict mode rejects the whole matrix through the mapper'() {
        given:
        def ctx = ApplicationContext.run(['micronaut.serde.deserialization.coercion-mode': 'STRICT'])
        def mapper = ctx.getBean(ObjectMapper)

        when:
        mapper.readValue(json, Plain)

        then:
        thrown SerdeException

        cleanup:
        ctx.close()

        where:
        json << [
                '{"number":42.5}',
                '{"number":"42"}',
                '{"number":true}',
                '{"number":[42]}',
                '{"text":1234}',
                '{"text":true}',
                '{"flag":1}',
                '{"amount":"42.5"}'
        ]
    }

    def 'strict mode still reads well shaped values'() {
        given:
        def ctx = ApplicationContext.run(['micronaut.serde.deserialization.coercion-mode': 'STRICT'])
        def mapper = ctx.getBean(ObjectMapper)
        def read = mapper.readValue('{"number":42,"text":"a","flag":true,"amount":42.5}', Plain)

        expect:
        read.number == 42
        read.text == 'a'
        read.flag
        read.amount == 42.5d

        cleanup:
        ctx.close()
    }

    def 'the default configuration keeps every historical coercion'() {
        given:
        def ctx = ApplicationContext.run()
        def mapper = ctx.getBean(ObjectMapper)

        expect:
        mapper.readValue('{"number":42.5}', Plain).number == 42
        mapper.readValue('{"number":"42"}', Plain).number == 42
        mapper.readValue('{"number":true}', Plain).number == 1
        mapper.readValue('{"number":[42]}', Plain).number == 42
        mapper.readValue('{"text":1234}', Plain).text == '1234'
        mapper.readValue('{"flag":1}', Plain).flag
        mapper.readValue('{"amount":"42.5"}', Plain).amount == 42.5d

        cleanup:
        ctx.close()
    }

    @Serdeable
    static class Plain {
        Integer number
        String text
        Boolean flag
        Double amount
    }

    @Serdeable
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes(@JsonSubTypes.Type(value = Sub.class, name = "sub"))
    static abstract class Base {
        Integer number
        String text
    }

    @Serdeable
    static class Sub extends Base {
    }
}
