package io.micronaut.serde.cbor

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.tree.JsonNode
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.serde.exceptions.SerdeException
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * The coercion configuration is format independent, so CBOR has to honour it on every path that
 * creates a decoder.
 */
class CborCoercionPolicySpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext strictContext = ApplicationContext.run(['micronaut.serde.deserialization.coercion-mode': 'STRICT'])

    @Shared
    @AutoCleanup
    ApplicationContext lenientContext = ApplicationContext.run()

    private CborObjectMapper strict() {
        strictContext.getBean(CborObjectMapper)
    }

    private CborObjectMapper lenient() {
        lenientContext.getBean(CborObjectMapper)
    }

    private static final JsonNode FLOAT_TREE = JsonNode.from([number: 42.5d])

    private byte[] floatBytes(CborObjectMapper mapper) {
        mapper.writeValueAsBytes(Argument.of(JsonNode), FLOAT_TREE)
    }

    void "reading bytes honours the policy"() {
        given:
        byte[] bytes = floatBytes(lenient())

        expect:
        lenient().readValue(bytes, Argument.of(Plain)).number == 42

        when:
        strict().readValue(bytes, Argument.of(Plain))

        then:
        thrown SerdeException
    }

    void "reading an input stream honours the policy"() {
        given:
        byte[] bytes = floatBytes(lenient())

        expect:
        lenient().readValue(new ByteArrayInputStream(bytes), Argument.of(Plain)).number == 42

        when:
        strict().readValue(new ByteArrayInputStream(bytes), Argument.of(Plain))

        then:
        thrown SerdeException
    }

    void "reading a tree honours the policy"() {
        expect:
        lenient().readValueFromTree(FLOAT_TREE, Argument.of(Plain)).number == 42

        when:
        strict().readValueFromTree(FLOAT_TREE, Argument.of(Plain))

        then:
        thrown SerdeException
    }

    void "a specific mapper honours the policy"() {
        given:
        byte[] bytes = floatBytes(lenient())

        expect:
        lenient().createSpecific(Argument.of(Plain)).readValue(bytes, Argument.of(Plain)).number == 42

        when:
        strict().createSpecific(Argument.of(Plain)).readValue(bytes, Argument.of(Plain))

        then:
        thrown SerdeException
    }

    void "updating from bytes honours the policy"() {
        given:
        byte[] bytes = floatBytes(lenient())

        when:
        def target = new Plain(number: 1)
        lenient().updateValue(target, Argument.of(Plain), bytes)

        then:
        target.number == 42

        when:
        strict().updateValue(new Plain(number: 1), Argument.of(Plain), bytes)

        then:
        thrown SerdeException
    }

    void "updating from a tree honours the policy"() {
        when:
        def target = new Plain(number: 1)
        lenient().updateValueFromTree(target, FLOAT_TREE)

        then:
        target.number == 42

        when:
        strict().updateValueFromTree(new Plain(number: 1), FLOAT_TREE)

        then:
        thrown SerdeException
    }

    void "well shaped values still read under the strict policy"() {
        given:
        byte[] bytes = lenient().writeValueAsBytes(Argument.of(JsonNode), JsonNode.from([number: 42]))

        expect:
        strict().readValue(bytes, Argument.of(Plain)).number == 42
        strict().readValueFromTree(JsonNode.from([number: 42]), Argument.of(Plain)).number == 42
    }

    @Serdeable
    static class Plain {
        Integer number
    }
}
