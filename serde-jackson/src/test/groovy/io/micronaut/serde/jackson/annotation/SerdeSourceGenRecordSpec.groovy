package io.micronaut.serde.jackson.annotation

import io.micronaut.core.type.Argument
import io.micronaut.serde.Decoder
import io.micronaut.serde.Deserializer
import io.micronaut.serde.Encoder
import io.micronaut.serde.LimitingStream
import io.micronaut.serde.SerdeIntrospections
import io.micronaut.serde.Serializer
import io.micronaut.serde.config.annotation.SerdeConfig
import io.micronaut.serde.jackson.JacksonDecoder
import io.micronaut.serde.jackson.JacksonEncoder
import io.micronaut.serde.jackson.JsonCompileSpec
import tools.jackson.core.json.JsonFactory

import java.lang.reflect.Modifier

class SerdeSourceGenRecordSpec extends JsonCompileSpec {

    void 'test record sourcegen serializer and deserializer are concrete and functional'() {
        given:
        def context = buildContext('test.TestRecord', '''
package test;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Introspected;

@Serdeable
@Introspected
public record TestRecord(String value, int count) {}
''')
        Class<?> recordType = context.classLoader.loadClass('test.TestRecord')
        def introspections = context.getBean(SerdeIntrospections)
        def metadata = introspections.getSerializableIntrospection(Argument.of(recordType)).annotationMetadata
        String serializerClassName = metadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_SERIALIZER_CLASS).orElse(null)
        String deserializerClassName = metadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_DESERIALIZER_CLASS).orElse(null)

        expect:
        metadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_SHAPE).orElse(null) == 'RECORD'
        metadata.booleanValue(SerdeConfig, SerdeConfig.SOURCEGEN_SERIALIZER_ELIGIBLE).orElse(false)
        metadata.booleanValue(SerdeConfig, SerdeConfig.SOURCEGEN_DESERIALIZER_ELIGIBLE).orElse(false)
        metadata.stringValues(SerdeConfig, SerdeConfig.SOURCEGEN_SERIALIZER_FALLBACK_REASONS).toSet().isEmpty()
        metadata.stringValues(SerdeConfig, SerdeConfig.SOURCEGEN_DESERIALIZER_FALLBACK_REASONS).toSet().isEmpty()
        serializerClassName != null
        deserializerClassName != null

        when:
        Class<?> serializerClass = context.classLoader.loadClass(serializerClassName)
        Class<?> deserializerClass = context.classLoader.loadClass(deserializerClassName)

        then:
        !Modifier.isAbstract(serializerClass.modifiers)
        !Modifier.isAbstract(deserializerClass.modifiers)

        when:
        Serializer serializer = (Serializer) serializerClass.getDeclaredConstructor().newInstance()
        Deserializer deserializer = (Deserializer) deserializerClass.getDeclaredConstructor().newInstance()
        def registry = jsonMapper.serdeRegistry
        Serializer.EncoderContext encoderContext = registry.newEncoderContext(Object)
        Deserializer.DecoderContext decoderContext = registry.newDecoderContext(Object)
        def jsonFactory = new JsonFactory()
        def output = new ByteArrayOutputStream()
        def record = recordType.getDeclaredConstructor(String, int).newInstance('hello', 7)
        def type = Argument.of(recordType)

        jsonFactory.createGenerator(output).withCloseable { generator ->
            Encoder encoder = JacksonEncoder.create(generator)
            serializer.serialize(encoder, encoderContext, type, record)
        }
        String json = output.toString('UTF-8')

        def deserialized
        jsonFactory.createParser(json).withCloseable { parser ->
            Decoder decoder = JacksonDecoder.create(parser, LimitingStream.DEFAULT_LIMITS)
            deserialized = deserializer.deserialize(decoder, decoderContext, type)
        }

        then:
        json == '{"value":"hello","count":7}'
        deserialized == record

        cleanup:
        context.close()
    }
}
