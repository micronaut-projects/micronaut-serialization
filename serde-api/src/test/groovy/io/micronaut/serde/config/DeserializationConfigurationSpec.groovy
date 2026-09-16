package io.micronaut.serde.config

import io.micronaut.context.ApplicationContext
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(startApplication = false)
class DeserializationConfigurationSpec extends Specification {
    @Inject
    DeserializationConfiguration deserializationConfiguration;

    void "micronaut.serde.deserialization.array-size-threshold defaults to 100"() {
        expect:
        deserializationConfiguration.arraySizeThreshold == 100
    }

    void "coercions are lenient by default"() {
        expect:
        deserializationConfiguration.coercionMode == CoercionMode.LENIENT
        deserializationConfiguration.isAcceptFloatAsInt()
        deserializationConfiguration.isAcceptStringAsNumber()
        deserializationConfiguration.isAcceptBooleanAsNumber()
        deserializationConfiguration.isAcceptNumberAsBoolean()
        deserializationConfiguration.isAcceptStringAsBoolean()
        deserializationConfiguration.isAcceptScalarAsString()
        deserializationConfiguration.isUnwrapSingleValueArrays()
        CoercionPolicy.fromConfiguration(deserializationConfiguration) == CoercionPolicy.LENIENT
    }

    void "a single coercion can be disabled"() {
        given:
        def context = ApplicationContext.run([
                'micronaut.serde.deserialization.accept-float-as-int': 'false'
        ])
        def configuration = context.getBean(DeserializationConfiguration)

        expect:
        !configuration.isAcceptFloatAsInt()
        configuration.isAcceptStringAsNumber()
        !CoercionPolicy.fromConfiguration(configuration).isAllowed(CoercionPolicy.Coercion.FLOAT_AS_INT)
        CoercionPolicy.fromConfiguration(configuration).isAllowed(CoercionPolicy.Coercion.STRING_AS_NUMBER)

        cleanup:
        context.close()
    }

    void "the strict mode disables every coercion"() {
        given:
        def context = ApplicationContext.run([
                'micronaut.serde.deserialization.coercion-mode': 'STRICT'
        ])
        def configuration = context.getBean(DeserializationConfiguration)

        expect:
        !configuration.isAcceptFloatAsInt()
        !configuration.isAcceptStringAsNumber()
        !configuration.isAcceptScalarAsString()
        !configuration.isUnwrapSingleValueArrays()
        CoercionPolicy.fromConfiguration(configuration) == CoercionPolicy.STRICT

        cleanup:
        context.close()
    }

    void "an explicit coercion overrides the strict mode"() {
        given:
        def context = ApplicationContext.run([
                'micronaut.serde.deserialization.coercion-mode'      : 'STRICT',
                'micronaut.serde.deserialization.accept-string-as-number': 'true'
        ])
        def configuration = context.getBean(DeserializationConfiguration)

        expect:
        !configuration.isAcceptFloatAsInt()
        configuration.isAcceptStringAsNumber()

        cleanup:
        context.close()
    }
}
