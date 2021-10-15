package io.micronaut.json

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class SerdeRegistrySpec extends Specification {
    // most of this is tested in GeneratedObjectCodecSpec
    // can we move this to serialization-base?
    @Shared @AutoCleanup  ApplicationContext context = ApplicationContext.run()

    def 'primitive lookup'() {
        given:
        def locator = context.getBean(SerdeRegistry)

        expect:
        locator.findDeserializer(Argument.INT) != null
        locator.findSerializer(Argument.INT) != null
    }
}
