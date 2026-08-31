package io.micronaut.serde.support.util

import io.micronaut.json.tree.JsonNode
import io.micronaut.serde.Decoder
import io.micronaut.serde.LimitingStream
import io.micronaut.serde.config.CoercionPolicy
import io.micronaut.serde.exceptions.SerdeException
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The tree decoder reads every value that is buffered before it is deserialized, so it has to
 * accept and reject exactly what the streaming decoders do.
 */
class JsonNodeDecoderCoercionSpec extends Specification {

    private static Decoder decoder(JsonNode node, CoercionPolicy policy) {
        return JsonNodeDecoder.create(node, LimitingStream.DEFAULT_LIMITS, policy)
    }

    private static final JsonNode FLOAT = JsonNode.createNumberNode(42.5d)
    private static final JsonNode INT = JsonNode.createNumberNode(42)
    private static final JsonNode STRING_NUMBER = JsonNode.createStringNode('42')
    private static final JsonNode TRUE = JsonNode.createBooleanNode(true)

    @Unroll
    void "a float node read as #method is a coercion"() {
        expect: 'truncated when allowed'
        decoder(FLOAT, CoercionPolicy.LENIENT)."$method"() == truncated

        when:
        decoder(FLOAT, CoercionPolicy.STRICT)."$method"()

        then:
        thrown SerdeException

        where:
        method             || truncated
        'decodeByte'       || (byte) 42
        'decodeShort'      || (short) 42
        'decodeInt'        || 42
        'decodeLong'       || 42L
        'decodeChar'       || (char) 42
        'decodeBigInteger' || BigInteger.valueOf(42)
    }

    @Unroll
    void "a string node read as #method is a coercion"() {
        expect:
        decoder(STRING_NUMBER, CoercionPolicy.LENIENT)."$method"() == parsed

        when:
        decoder(STRING_NUMBER, CoercionPolicy.STRICT)."$method"()

        then:
        thrown SerdeException

        where:
        method             || parsed
        'decodeByte'       || (byte) 42
        'decodeShort'      || (short) 42
        'decodeInt'        || 42
        'decodeLong'       || 42L
        'decodeFloat'      || 42f
        'decodeDouble'     || 42d
        'decodeBigInteger' || BigInteger.valueOf(42)
        'decodeBigDecimal' || BigDecimal.valueOf(42)
    }

    @Unroll
    void "a boolean node read as #method is a coercion"() {
        expect:
        decoder(TRUE, CoercionPolicy.LENIENT)."$method"() == one

        when:
        decoder(TRUE, CoercionPolicy.STRICT)."$method"()

        then:
        thrown SerdeException

        where:
        method             || one
        'decodeByte'       || (byte) 1
        'decodeShort'      || (short) 1
        'decodeInt'        || 1
        'decodeLong'       || 1L
        'decodeFloat'      || 1f
        'decodeDouble'     || 1d
        'decodeBigInteger' || BigInteger.ONE
        'decodeBigDecimal' || BigDecimal.ONE
    }

    void "a number or boolean node read as a string is a coercion"() {
        expect:
        decoder(INT, CoercionPolicy.LENIENT).decodeString() == '42'
        decoder(FLOAT, CoercionPolicy.LENIENT).decodeString() == '42.5'
        decoder(TRUE, CoercionPolicy.LENIENT).decodeString() == 'true'

        when:
        decoder(INT, CoercionPolicy.STRICT).decodeString()

        then:
        thrown SerdeException

        when:
        decoder(TRUE, CoercionPolicy.STRICT).decodeString()

        then:
        thrown SerdeException
    }

    void "a number or string node read as a boolean is a coercion"() {
        expect:
        decoder(INT, CoercionPolicy.LENIENT).decodeBoolean()
        decoder(FLOAT, CoercionPolicy.LENIENT).decodeBoolean()
        !decoder(JsonNode.createNumberNode(0), CoercionPolicy.LENIENT).decodeBoolean()
        decoder(JsonNode.createStringNode('true'), CoercionPolicy.LENIENT).decodeBoolean()
        !decoder(JsonNode.createStringNode('nope'), CoercionPolicy.LENIENT).decodeBoolean()

        when:
        decoder(INT, CoercionPolicy.STRICT).decodeBoolean()

        then:
        thrown SerdeException

        when:
        decoder(JsonNode.createStringNode('true'), CoercionPolicy.STRICT).decodeBoolean()

        then:
        thrown SerdeException
    }

    @Unroll
    void "a single element array read as #method is a coercion"() {
        given:
        def array = JsonNode.createArrayNode([INT])

        expect:
        decoder(array, CoercionPolicy.LENIENT)."$method"() == unwrapped

        when:
        decoder(array, CoercionPolicy.STRICT)."$method"()

        then:
        thrown SerdeException

        where:
        method             || unwrapped
        'decodeByte'       || (byte) 42
        'decodeShort'      || (short) 42
        'decodeInt'        || 42
        'decodeLong'       || 42L
        'decodeFloat'      || 42f
        'decodeDouble'     || 42d
        'decodeBigInteger' || BigInteger.valueOf(42)
        'decodeBigDecimal' || BigDecimal.valueOf(42)
        'decodeChar'       || (char) 42
    }

    void "a single element string array read as a string is a coercion"() {
        given:
        def array = JsonNode.createArrayNode([JsonNode.createStringNode('a')])

        expect:
        decoder(array, CoercionPolicy.LENIENT).decodeString() == 'a'

        when:
        decoder(array, CoercionPolicy.STRICT).decodeString()

        then:
        thrown SerdeException
    }

    void "a single element boolean array read as a boolean is a coercion"() {
        given:
        def array = JsonNode.createArrayNode([TRUE])

        expect:
        decoder(array, CoercionPolicy.LENIENT).decodeBoolean()

        when:
        decoder(array, CoercionPolicy.STRICT).decodeBoolean()

        then:
        thrown SerdeException
    }

    void "values of the right shape are read under the strict policy"() {
        expect:
        decoder(INT, CoercionPolicy.STRICT).decodeInt() == 42
        decoder(INT, CoercionPolicy.STRICT).decodeLong() == 42L
        decoder(INT, CoercionPolicy.STRICT).decodeByte() == (byte) 42
        decoder(INT, CoercionPolicy.STRICT).decodeShort() == (short) 42
        decoder(INT, CoercionPolicy.STRICT).decodeBigInteger() == BigInteger.valueOf(42)
        decoder(INT, CoercionPolicy.STRICT).decodeChar() == (char) 42
        decoder(FLOAT, CoercionPolicy.STRICT).decodeFloat() == 42.5f
        decoder(FLOAT, CoercionPolicy.STRICT).decodeDouble() == 42.5d
        decoder(FLOAT, CoercionPolicy.STRICT).decodeBigDecimal() == BigDecimal.valueOf(42.5d)
        decoder(JsonNode.createStringNode('a'), CoercionPolicy.STRICT).decodeString() == 'a'
        decoder(TRUE, CoercionPolicy.STRICT).decodeBoolean()
    }

    @Unroll
    void "a single character string is the natural shape of a char (#policyName)"() {
        expect: 'accepted whatever the policy, like the streaming decoder'
        decoder(JsonNode.createStringNode('a'), policy).decodeChar() == (char) 'a'

        when: 'more than one character is not a char'
        decoder(JsonNode.createStringNode('42'), policy).decodeChar()

        then:
        thrown SerdeException

        when:
        decoder(JsonNode.createStringNode(''), policy).decodeChar()

        then:
        thrown SerdeException

        where:
        policyName | policy
        'lenient'  | CoercionPolicy.LENIENT
        'strict'   | CoercionPolicy.STRICT
    }

    void "an unparseable string is reported whatever the policy"() {
        when:
        decoder(JsonNode.createStringNode('nope'), CoercionPolicy.LENIENT).decodeInt()

        then:
        thrown SerdeException
    }

    void "the decoder exposes its policy and passes it to buffered decoders"() {
        given:
        def d = decoder(FLOAT, CoercionPolicy.STRICT)

        expect:
        d.getCoercionPolicy().is(CoercionPolicy.STRICT)
        decoder(FLOAT, CoercionPolicy.LENIENT).decodeBuffer().getCoercionPolicy().is(CoercionPolicy.LENIENT)

        when:
        decoder(FLOAT, CoercionPolicy.STRICT).decodeBuffer().decodeInt()

        then:
        thrown SerdeException
    }

    void "object and array decoders inherit the policy"() {
        given:
        def object = JsonNode.createObjectNode([value: FLOAT])
        def array = JsonNode.createArrayNode([FLOAT, FLOAT])

        when:
        def objectDecoder = decoder(object, CoercionPolicy.STRICT).decodeObject()
        objectDecoder.decodeKey()
        objectDecoder.decodeInt()

        then:
        thrown SerdeException

        when:
        decoder(array, CoercionPolicy.STRICT).decodeArray().decodeInt()

        then:
        thrown SerdeException
    }
}
