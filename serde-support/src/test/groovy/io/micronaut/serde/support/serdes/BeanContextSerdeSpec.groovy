package io.micronaut.serde.support.serdes

import io.micronaut.context.BeanContext
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.Deserializer
import io.micronaut.serde.LimitingStream
import io.micronaut.serde.Serde
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.Serializer
import io.micronaut.serde.support.deserializers.ObjectDeserializer
import io.micronaut.serde.support.serializers.ObjectSerializer
import io.micronaut.serde.support.util.JsonNodeDecoder
import io.micronaut.serde.support.util.JsonNodeEncoder
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

    void "test retrieving InstantSerde"() {
        when:
        def byteArraySerde = beanContext.getBean(InstantSerde)

        then:
        byteArraySerde.getClass() == InstantSerde
    }

    void "test retrieving EnumSerde"() {
        when:
        def enumSerde = beanContext.getBean(Argument.of(Serde, MyEnum))

        then:
        enumSerde.getClass() == EnumSerde
    }

    void "test retrieving Map serializer / deserializer"() {
        when:
        def value = Map.of("name", "Denis")
        def serdeRegistry = beanContext.getBean(SerdeRegistry)
        def jsonMapper = beanContext.getBean(JsonMapper)
        def encoderContext = serdeRegistry.newEncoderContext(null)
        def decoderContext = serdeRegistry.newDecoderContext(null)
        def argument = Argument.mapOf(String, Object)
        def serializer =  beanContext.getBean(Argument.of(Serializer, argument)).createSpecific(encoderContext, argument)
        def deserializer = beanContext.getBean(Argument.of(Deserializer, argument)).createSpecific(decoderContext, argument)
        def encoder = JsonNodeEncoder.create()

        then:
            serializer.serialize(encoder, encoderContext, argument, value)
            def jsonNode = encoder.getCompletedValue()
            def str = jsonMapper.writeValueAsString(jsonNode)
        then:
            str == '{"name":"Denis"}'
        when:
            def decoder = JsonNodeDecoder.create(jsonNode, LimitingStream.DEFAULT_LIMITS)
            def deserResult = deserializer.deserialize(decoder, decoderContext, argument)
        then:
            deserResult == value
    }

    static enum MyEnum {}
}
