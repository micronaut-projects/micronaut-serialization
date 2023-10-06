package io.micronaut.serde.config

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(startApplication = false)
class SerdeConfigurationSpec extends Specification {

    @Inject
    SerdeConfiguration serdeConfiguration

    void  "inet address as numeric defaults to false" () {
        expect:
        !serdeConfiguration.isInetAddressAsNumeric()
    }
}
