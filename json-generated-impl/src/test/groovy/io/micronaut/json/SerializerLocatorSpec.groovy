package io.micronaut.json

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class SerializerLocatorSpec extends Specification {
    // most of this is tested in GeneratedObjectCodecSpec
    // can we move this to serialization-base?

    def 'primitive lookup'() {
        given:
        def locator = ApplicationContext.run().getBean(SerdeRegistry)

        expect:
        locator.findInvariantDeserializer(int.class) != null
        locator.findContravariantSerializer(int.class) != null
    }
}
