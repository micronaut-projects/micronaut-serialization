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

    void 'test record generated deserializer shape plus createSpecific parity for duplicate unknown and null defaults'() {
        given:
        def context = buildContext('test.ParityRecord', '''
package test;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Introspected;

@Serdeable
@Introspected
public record ParityRecord(String value, int count) {}
''')
        Class<?> recordType = context.classLoader.loadClass('test.ParityRecord')
        def introspections = context.getBean(SerdeIntrospections)
        def metadata = introspections.getSerializableIntrospection(Argument.of(recordType)).annotationMetadata
        String deserializerClassName = metadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_DESERIALIZER_CLASS).orElse(null)
        Class<?> deserializerClass = context.classLoader.loadClass(deserializerClassName)
        def registry = jsonMapper.serdeRegistry
        Deserializer.DecoderContext decoderContext = registry.newDecoderContext(Object)
        def type = Argument.of(recordType)
        Deserializer defaultDeserializer = (Deserializer) deserializerClass.getDeclaredConstructor().newInstance()
        Deserializer specificDeserializer = defaultDeserializer.createSpecific(decoderContext, type)

        expect:
        deserializerClass.declaredFields*.name.any { it.startsWith('KEY_') }
        deserializerClass.declaredFields*.name.any { it.startsWith('ARGUMENT_') }
        !deserializerClass.declaredFields*.name.any { it.startsWith('DESERIALIZER_') }
        specificDeserializer.class == deserializerClass

        when:
        def fromDefaultMissing = deserializeValue(defaultDeserializer, decoderContext, type, '{"value":"hello"}')
        def fromSpecificMissing = deserializeValue(specificDeserializer, decoderContext, type, '{"value":"hello"}')

        then:
        fromDefaultMissing.value() == 'hello'
        fromSpecificMissing.value() == 'hello'
        fromDefaultMissing.count() == 0
        fromSpecificMissing.count() == 0

        when:
        def duplicateDefaultFailure = captureFailure {
            deserializeValue(defaultDeserializer, decoderContext, type, '{"value":"a","value":"b","count":1}')
        }
        def duplicateSpecificFailure = captureFailure {
            deserializeValue(specificDeserializer, decoderContext, type, '{"value":"a","value":"b","count":1}')
        }

        then:
        duplicateDefaultFailure != null
        duplicateSpecificFailure != null
        duplicateDefaultFailure.message?.contains('value')
        duplicateSpecificFailure.message?.contains('value')

        when:
        def unknownDefaultFailure = captureFailure {
            deserializeValue(defaultDeserializer, decoderContext, type, '{"value":"a","count":1,"extra":2}')
        }
        def unknownSpecificFailure = captureFailure {
            deserializeValue(specificDeserializer, decoderContext, type, '{"value":"a","count":1,"extra":2}')
        }

        then:
        (unknownDefaultFailure == null) == (unknownSpecificFailure == null)
        if (unknownDefaultFailure != null) {
            assert unknownDefaultFailure.message?.contains('extra')
            assert unknownSpecificFailure.message?.contains('extra')
        }

        cleanup:
        context.close()
    }

    private static Object deserializeValue(Deserializer deserializer,
                                           Deserializer.DecoderContext decoderContext,
                                           Argument type,
                                           String json) {
        def jsonFactory = new JsonFactory()
        def result
        jsonFactory.createParser(json).withCloseable { parser ->
            Decoder decoder = JacksonDecoder.create(parser, LimitingStream.DEFAULT_LIMITS)
            result = deserializer.deserialize(decoder, decoderContext, type)
        }
        result
    }

    private static Exception captureFailure(Closure<?> action) {
        try {
            action.call()
            return null
        } catch (Exception e) {
            return e
        }
    }
}
