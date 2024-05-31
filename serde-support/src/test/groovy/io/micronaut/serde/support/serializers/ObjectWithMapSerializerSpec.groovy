package io.micronaut.serde.support.serializers


import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.LimitingStream
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.support.ObjectWithMap
import io.micronaut.serde.support.util.JsonNodeDecoder
import io.micronaut.serde.support.util.JsonNodeEncoder
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class ObjectWithMapSerializerSpec extends Specification {

    @Inject
    SerdeRegistry serdeRegistry

    @Inject
    JsonMapper jsonMapper

    def 'test serialize object with map'() {
        given:
        def obj = new ObjectWithMap("Object1", Map.of("item1", 1L), Map.of(1, 1L))
        def encoderContext = serdeRegistry.newEncoderContext(null)
        def decoderContext = serdeRegistry.newDecoderContext(null)
        def argument = Argument.of(ObjectWithMap)
        def serializer = serdeRegistry.findSerializer(argument).createSpecific(encoderContext, argument)
        def deserializer = serdeRegistry.findDeserializer(argument).createSpecific(decoderContext, argument)
        def encoder = JsonNodeEncoder.create()
        when:
        serializer.serialize(encoder, encoderContext, argument, obj)
        def jsonNode = encoder.getCompletedValue()
        def str = jsonMapper.writeValueAsString(jsonNode)
        then:
        str == '{"name":"Object1","stringLongMap":{"item1":1},"integerLongMap":{"1":1}}'
        when:
        def decoder = JsonNodeDecoder.create(jsonNode, LimitingStream.DEFAULT_LIMITS)
        def deserResult = deserializer.deserialize(decoder, decoderContext, argument)
        then:
        deserResult.name == obj.name
        deserResult.stringLongMap == obj.stringLongMap
        deserResult.integerLongMap == obj.integerLongMap
    }

}
