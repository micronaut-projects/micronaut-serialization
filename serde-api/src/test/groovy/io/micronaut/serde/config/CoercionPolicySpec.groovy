package io.micronaut.serde.config

import io.micronaut.serde.config.CoercionPolicy.Coercion
import io.micronaut.serde.config.CoercionPolicy.Shape
import io.micronaut.serde.config.CoercionPolicy.Target
import spock.lang.Specification
import spock.lang.Unroll

class CoercionPolicySpec extends Specification {

    void "the lenient policy allows everything"() {
        expect:
        Coercion.values().every { CoercionPolicy.LENIENT.isAllowed(it) }
    }

    void "the strict policy allows nothing"() {
        expect:
        Coercion.values().every { !CoercionPolicy.STRICT.isAllowed(it) }
    }

    void "a null configuration is lenient"() {
        expect:
        CoercionPolicy.fromConfiguration(null).is(CoercionPolicy.LENIENT)
    }

    void "a fully lenient configuration resolves to the shared lenient instance"() {
        expect:
        CoercionPolicy.fromConfiguration(new DeserializationConfiguration() {
            @Override
            boolean isIgnoreUnknown() { true }

            @Override
            int getArraySizeThreshold() { 100 }

            @Override
            boolean isStrictNullable() { false }
        }).is(CoercionPolicy.LENIENT)
    }

    void "a strict configuration resolves to the shared strict instance"() {
        expect:
        CoercionPolicy.fromConfiguration(strict()).is(CoercionPolicy.STRICT)
    }

    void "a mixed configuration resolves to its own instance"() {
        given:
        def policy = CoercionPolicy.fromConfiguration(new StrictConfiguration() {
            @Override
            boolean isAcceptStringAsNumber() { true }
        })

        expect:
        policy.isAllowed(Coercion.STRING_AS_NUMBER)
        !policy.isAllowed(Coercion.FLOAT_AS_INT)
        !policy.is(CoercionPolicy.LENIENT)
        !policy.is(CoercionPolicy.STRICT)
    }

    void "policies with the same coercions are equal"() {
        given:
        def one = CoercionPolicy.fromConfiguration(new StrictConfiguration() {
            @Override
            boolean isAcceptStringAsNumber() { true }
        })
        def two = CoercionPolicy.fromConfiguration(new StrictConfiguration() {
            @Override
            boolean isAcceptStringAsNumber() { true }
        })

        expect:
        one == two
        one.hashCode() == two.hashCode()
        one != CoercionPolicy.STRICT
        one != 'not a policy'
    }

    void "the policy describes itself"() {
        expect:
        CoercionPolicy.LENIENT.toString() == 'CoercionPolicy.LENIENT'
        CoercionPolicy.STRICT.toString() == 'CoercionPolicy.STRICT'
        CoercionPolicy.fromConfiguration(new StrictConfiguration() {
            @Override
            boolean isAcceptStringAsNumber() { true }

            @Override
            boolean isAcceptScalarAsString() { true }
        }).toString() == 'CoercionPolicy[STRING_AS_NUMBER, SCALAR_AS_STRING]'
    }

    @Unroll
    void "reading a #shape as #target needs #coercion"() {
        expect:
        CoercionPolicy.coercion(target, shape) == coercion
        CoercionPolicy.LENIENT.allowedShapes(target) & shape.bit()
        (CoercionPolicy.STRICT.allowedShapes(target) & shape.bit()) == (coercion == null ? shape.bit() : 0)

        where:
        target          | shape                | coercion
        Target.INTEGER  | Shape.INTEGER_NUMBER | null
        Target.INTEGER  | Shape.FLOAT_NUMBER   | Coercion.FLOAT_AS_INT
        Target.INTEGER  | Shape.STRING         | Coercion.STRING_AS_NUMBER
        Target.INTEGER  | Shape.BOOLEAN        | Coercion.BOOLEAN_AS_NUMBER
        Target.INTEGER  | Shape.ARRAY          | Coercion.UNWRAP_SINGLE_VALUE_ARRAY
        Target.INTEGER  | Shape.OTHER          | null
        Target.DECIMAL  | Shape.INTEGER_NUMBER | null
        Target.DECIMAL  | Shape.FLOAT_NUMBER   | null
        Target.DECIMAL  | Shape.STRING         | Coercion.STRING_AS_NUMBER
        Target.DECIMAL  | Shape.BOOLEAN        | Coercion.BOOLEAN_AS_NUMBER
        Target.DECIMAL  | Shape.ARRAY          | Coercion.UNWRAP_SINGLE_VALUE_ARRAY
        Target.BOOLEAN  | Shape.BOOLEAN        | null
        Target.BOOLEAN  | Shape.INTEGER_NUMBER | Coercion.NUMBER_AS_BOOLEAN
        Target.BOOLEAN  | Shape.FLOAT_NUMBER   | Coercion.NUMBER_AS_BOOLEAN
        Target.BOOLEAN  | Shape.STRING         | Coercion.STRING_AS_BOOLEAN
        Target.BOOLEAN  | Shape.ARRAY          | Coercion.UNWRAP_SINGLE_VALUE_ARRAY
        Target.STRING   | Shape.STRING         | null
        Target.STRING   | Shape.INTEGER_NUMBER | Coercion.SCALAR_AS_STRING
        Target.STRING   | Shape.FLOAT_NUMBER   | Coercion.SCALAR_AS_STRING
        Target.STRING   | Shape.BOOLEAN        | Coercion.SCALAR_AS_STRING
        Target.STRING   | Shape.ARRAY          | Coercion.UNWRAP_SINGLE_VALUE_ARRAY
        Target.CHAR     | Shape.STRING         | null
        Target.CHAR     | Shape.INTEGER_NUMBER | null
        Target.CHAR     | Shape.FLOAT_NUMBER   | Coercion.FLOAT_AS_INT
        Target.CHAR     | Shape.BOOLEAN        | Coercion.BOOLEAN_AS_NUMBER
        Target.CHAR     | Shape.ARRAY          | Coercion.UNWRAP_SINGLE_VALUE_ARRAY
    }

    void "every coercion has a message"() {
        expect:
        Coercion.values().every { !it.message().isEmpty() }
    }

    private static DeserializationConfiguration strict() {
        return new StrictConfiguration()
    }

    static class StrictConfiguration implements DeserializationConfiguration {
        @Override
        boolean isIgnoreUnknown() { true }

        @Override
        int getArraySizeThreshold() { 100 }

        @Override
        boolean isStrictNullable() { false }

        @Override
        CoercionMode getCoercionMode() { CoercionMode.STRICT }
    }
}
