package io.micronaut.serde.support.serdes

import io.micronaut.context.BeanContext
import io.micronaut.core.type.Argument
import io.micronaut.serde.Serde
import io.micronaut.serde.support.deserializers.ObjectDeserializer
import io.micronaut.serde.support.serializers.ObjectSerializer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import java.time.Duration

@MicronautTest
class BeanContextSerdeSpec extends Specification {
    @Inject BeanContext beanContext

    void "test retrieving serdes from the bean context"() {
        when:
        def result = beanContext.getBean(Argument.of(Serde, Duration))

        then:
        result.getClass() == DurationSerde
    }

    void "test retrieving object serializer from the bean context"() {
        when:
        beanContext.getBean(ObjectSerializer)

        then:
        noExceptionThrown()
    }

    void "test retrieving object deserializer from the bean context"() {
        when:
        beanContext.getBean(ObjectDeserializer)

        then:
        noExceptionThrown()
    }

    void "test retrieving object array serde from the bean context"() {
        when:
        beanContext.getBean(ObjectArraySerde)

        then:
        noExceptionThrown()
    }

    void "test retrieving byte array serde from the bean context"() {
        when:
        def byteArraySerde = beanContext.getBean(Argument.of(Serde, byte[]))

        then:
        byteArraySerde.getClass() == ByteArraySerde
    }
}
