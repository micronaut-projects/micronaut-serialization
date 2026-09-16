package io.micronaut.serde.json.stream

import io.micronaut.context.ApplicationContext
import io.micronaut.serde.Decoder
import io.micronaut.serde.LimitingStream
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.serde.config.CoercionPolicy
import io.micronaut.serde.exceptions.SerdeException
import jakarta.json.Json
import jakarta.json.stream.JsonParser
import org.intellij.lang.annotations.Language
import spock.lang.Specification
import spock.lang.Unroll

class JsonpCoercionPolicySpec extends Specification {

    private static Decoder createDecoder(@Language('json') String json, CoercionPolicy policy) {
        JsonParser parser = Json.createParser(new StringReader(json))
        return new JsonParserDecoder(parser, LimitingStream.DEFAULT_LIMITS, policy)
    }

    @Unroll
    def 'the streaming decoder honours the policy for #json read as #method'() {
        when:
        createDecoder(json, CoercionPolicy.STRICT)."$method"()

        then:
        thrown SerdeException

        when: 'the same input is lenient'
        def lenient = createDecoder(json, CoercionPolicy.LENIENT)."$method"()

        then:
        lenient == coerced

        where:
        json     | method             || coerced
        '42.5'   | 'decodeInt'        || 42
        '42.5'   | 'decodeLong'       || 42L
        '42.5'   | 'decodeBigInteger' || BigInteger.valueOf(42)
        '"42"'   | 'decodeInt'        || 42
        '"42.5"' | 'decodeDouble'     || 42.5d
        'true'   | 'decodeInt'        || 1
        '1'      | 'decodeBoolean'    || true
        '1234'   | 'decodeString'     || '1234'
        '[42]'   | 'decodeInt'        || 42
    }

    def 'values of the right shape are untouched'() {
        expect:
        createDecoder('42', CoercionPolicy.STRICT).decodeInt() == 42
        createDecoder('42.5', CoercionPolicy.STRICT).decodeDouble() == 42.5d
        createDecoder('42', CoercionPolicy.STRICT).decodeDouble() == 42d
        createDecoder('"a"', CoercionPolicy.STRICT).decodeString() == 'a'
        createDecoder('true', CoercionPolicy.STRICT).decodeBoolean()
    }

    def 'the mapper picks the policy up from the configuration'() {
        given:
        def ctx = ApplicationContext.run(['micronaut.serde.deserialization.coercion-mode': 'STRICT'])
        def mapper = ctx.getBean(JsonStreamMapper)

        when:
        mapper.readValue('{"number":42.5}', Plain)

        then:
        thrown SerdeException

        when:
        mapper.readValue('{"number":"42"}', Plain)

        then:
        thrown SerdeException

        and: 'a well shaped value still reads'
        mapper.readValue('{"number":42}', Plain).number == 42

        cleanup:
        ctx.close()
    }

    def 'the default configuration is unchanged'() {
        given:
        def ctx = ApplicationContext.run()
        def mapper = ctx.getBean(JsonStreamMapper)

        expect:
        mapper.readValue('{"number":42.5}', Plain).number == 42
        mapper.readValue('{"number":"42"}', Plain).number == 42
        mapper.readValue('{"number":[42]}', Plain).number == 42

        cleanup:
        ctx.close()
    }

    @Serdeable
    static class Plain {
        Integer number
    }
}
