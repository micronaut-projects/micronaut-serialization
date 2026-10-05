package io.micronaut.serde.jackson.specific

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.serde.Decoder
import io.micronaut.serde.Deserializer
import io.micronaut.serde.Encoder
import io.micronaut.serde.FormatConfiguration
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.Serializer
import io.micronaut.serde.annotation.CacheableSpecificSerde
import io.micronaut.serde.jackson.compiletime.SourceGenGeneratedShape
import spock.lang.AutoCleanup
import spock.lang.Specification

class SpecificBeanSerdeCacheSpec extends Specification {

    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    ObjectMapper objectMapper = context.getBean(ObjectMapper)
    SerdeRegistry registry = context.getBean(SerdeRegistry)

    def setup() {
        PointSerde.DESERIALIZERS.set(0)
        PointSerde.SERIALIZERS.set(0)
        TagSerde.DESERIALIZERS.set(0)
        TagSerde.SERIALIZERS.set(0)
    }

    void "the specific serde bean is created once per type"() {
        expect:
        (1..10).every {
            objectMapper.readValue('"1,2"', Point) == new Point(1, 2)
                && objectMapper.writeValueAsString(new Point(3, 4)) == '"3,4"'
                && objectMapper.readValue('"#a"', Tag) == new Tag('a')
                && objectMapper.writeValueAsString(new Tag('b')) == '"#b"'
        }
        PointSerde.DESERIALIZERS.get() == 1
        PointSerde.SERIALIZERS.get() == 1
        TagSerde.DESERIALIZERS.get() == 1
        TagSerde.SERIALIZERS.get() == 1
    }

    void "the formatted specific serdes are cached per format"() {
        given:
        def dash = new FormatConfiguration('-', FormatConfiguration.Shape.ANY, null, null, null, FormatConfiguration.DEFAULT_RADIX)
        def colon = new FormatConfiguration(':', FormatConfiguration.Shape.ANY, null, null, null, FormatConfiguration.DEFAULT_RADIX)

        expect:
        (1..10).every {
            deserialize(Argument.of(Point), '1-2', dash) == new Point(1, 2)
                && deserialize(Argument.of(Point), '1:2', colon) == new Point(1, 2)
                && deserialize(Argument.of(Point), '1,2', null) == new Point(1, 2)
                && serialize(Argument.of(Point), new Point(3, 4), dash) == '3-4'
                && serialize(Argument.of(Point), new Point(3, 4), colon) == '3:4'
                && serialize(Argument.of(Point), new Point(3, 4), null) == '3,4'
        }
        PointSerde.DESERIALIZERS.get() == 3
        PointSerde.SERIALIZERS.get() == 3
    }

    void "the specific serdes of a context with a view are not cached"() {
        given:
        def viewMapper = objectMapper.cloneWithViewClass(SpecificBeanSerdeCacheSpec)

        expect:
        (1..3).every {
            viewMapper.readValue('"1,2"', Point) == new Point(1, 2)
                && viewMapper.writeValueAsString(new Point(3, 4)) == '"3,4"'
        }
        PointSerde.DESERIALIZERS.get() == 3
        PointSerde.SERIALIZERS.get() == 3
    }

    void "the generated serdes are created once per type"() {
        given:
        def type = Argument.of(SourceGenGeneratedShape)
        def decoderContext1 = registry.newDecoderContext(null)
        def decoderContext2 = registry.newDecoderContext(null)
        def encoderContext1 = registry.newEncoderContext(null)
        def encoderContext2 = registry.newEncoderContext(null)

        expect:
        context.getBeanDefinitions(Deserializer).find { it.beanType.simpleName == 'SerdeSourceGenGeneratedShapeDeserializer' }
            .hasDeclaredAnnotation(CacheableSpecificSerde)
        context.getBeanDefinitions(Serializer).find { it.beanType.simpleName == 'SerdeSourceGenGeneratedShapeSerializer' }
            .hasDeclaredAnnotation(CacheableSpecificSerde)
        registry.findDeserializer(type).createSpecific(decoderContext1, type)
            .is(registry.findDeserializer(type).createSpecific(decoderContext2, type))
        registry.findSerializer(type).createSpecific(encoderContext1, type)
            .is(registry.findSerializer(type).createSpecific(encoderContext2, type))
        objectMapper.readValue('{"name":"a","count":1}', SourceGenGeneratedShape) == new SourceGenGeneratedShape('a', 1)
        objectMapper.writeValueAsString(new SourceGenGeneratedShape('b', 2)) == '{"name":"b","count":2}'
    }

    void "a generated serde passes the context of each document to a nested contextual serde"() {
        expect: 'the holder is handled by the generated serdes'
        context.getBeanDefinitions(Deserializer).any { it.beanType.simpleName == 'SerdeSecretHolderDeserializer' }
        context.getBeanDefinitions(Serializer).any { it.beanType.simpleName == 'SerdeSecretHolderSerializer' }

        and: 'every document reaches the nested serde with its own context'
        (1..3).every {
            objectMapper.readValue('{"secret":"s"}', SecretHolder) == new SecretHolder(new Secret('s'))
                && objectMapper.writeValueAsString(new SecretHolder(new Secret('t'))) == '{"secret":"t"}'
        }
    }

    private Object deserialize(Argument<?> type, String value, FormatConfiguration format) {
        Decoder decoder = Stub {
            decodeString() >> value
        }
        try (def decoderContext = registry.newDecoderContext(null)) {
            def deserializer = registry.findDeserializer(type)
            def specific = format == null
                ? deserializer.createSpecific(decoderContext, type)
                : deserializer.createSpecific(decoderContext, type, format)
            return specific.deserialize(decoder, decoderContext, type)
        }
    }

    private String serialize(Argument<?> type, Object value, FormatConfiguration format) {
        String written = null
        Encoder encoder = Stub {
            encodeString(_) >> { String s -> written = s }
        }
        try (def encoderContext = registry.newEncoderContext(null)) {
            def serializer = registry.findSerializer(type)
            def specific = format == null
                ? serializer.createSpecific(encoderContext, type)
                : serializer.createSpecific(encoderContext, type, format)
            specific.serialize(encoder, encoderContext, type, value)
        }
        return written
    }
}
