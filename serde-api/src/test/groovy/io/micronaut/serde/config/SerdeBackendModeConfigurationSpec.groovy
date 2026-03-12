package io.micronaut.serde.config

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(startApplication = false)
@Property(name = "micronaut.serde.backend-mode", value = "INTROSPECTION")
class SerdeBackendModeConfigurationSpec extends Specification {

    @Inject
    SerdeConfiguration serdeConfiguration

    void "micronaut.serde.backend-mode binds from configuration"() {
        expect:
        serdeConfiguration.backendMode == SerdeBackendMode.INTROSPECTION
    }
}
