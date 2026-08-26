package io.micronaut.serde.config

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

    void "micronaut.serde.deserialization.accept-float-as-int defaults to true"() {
        expect:
        deserializationConfiguration.isAcceptFloatAsInt()
    }

    void "micronaut.serde.deserialization.accept-float-as-int can be disabled"() {
        given:
        def context = ApplicationContext.run([
                'micronaut.serde.deserialization.accept-float-as-int': 'false'
        ])

        expect:
        !context.getBean(DeserializationConfiguration).isAcceptFloatAsInt()

        cleanup:
        context.close()
    }
}
