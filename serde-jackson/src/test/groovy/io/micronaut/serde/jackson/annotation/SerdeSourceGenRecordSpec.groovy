package io.micronaut.serde.jackson.annotation

import io.micronaut.core.type.Argument
import io.micronaut.serde.Decoder
import io.micronaut.serde.Deserializer
import io.micronaut.serde.Encoder
import io.micronaut.serde.LimitingStream
import io.micronaut.serde.SerdeIntrospections
import io.micronaut.serde.Serializer
import io.micronaut.serde.config.annotation.SerdeConfig
import io.micronaut.serde.exceptions.SerdeException
import io.micronaut.serde.jackson.JacksonDecoder
import io.micronaut.serde.jackson.JacksonEncoder
import io.micronaut.serde.jackson.JsonCompileSpec
import tools.jackson.core.json.JsonFactory

import java.lang.reflect.Modifier
import jakarta.inject.Singleton

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
        serializerClassName != null
        deserializerClassName != null

        when:
        Class<?> serializerClass = context.classLoader.loadClass(serializerClassName)
        Class<?> deserializerClass = context.classLoader.loadClass(deserializerClassName)

        then:
        !Modifier.isAbstract(serializerClass.modifiers)
        !Modifier.isAbstract(deserializerClass.modifiers)
        serializerClass.getAnnotation(Singleton) != null
        deserializerClass.getAnnotation(Singleton) != null

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

    void 'test record generated deserializer shape plus createSpecific parity for duplicate unknown null defaults and property path failures'() {
        given:
        def context = buildContext('test.ParityRecord', '''
package test;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Introspected;

@Serdeable
@Introspected
public record ParityRecord(String value, int count, java.util.List<String> tags) {}
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
        deserializerClass.declaredFields*.name.any { it.startsWith('DESERIALIZER_') }
        specificDeserializer.class == deserializerClass

        when:
        def fromDefaultMissing = deserializeValue(defaultDeserializer, decoderContext, type, '{"value":"hello","tags":["a","b"]}')
        def fromSpecificMissing = deserializeValue(specificDeserializer, decoderContext, type, '{"value":"hello","tags":["a","b"]}')

        then:
        fromDefaultMissing.value() == 'hello'
        fromSpecificMissing.value() == 'hello'
        fromDefaultMissing.count() == 0
        fromSpecificMissing.count() == 0
        fromDefaultMissing.tags().toString() == '[a, b]'
        fromSpecificMissing.tags().toString() == '[a, b]'

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

        when:
        def scalarPathDefaultFailure = captureFailure {
            deserializeValue(defaultDeserializer, decoderContext, type, '{"value":"hello","count":"oops","tags":["x"]}')
        }
        def scalarPathSpecificFailure = captureFailure {
            deserializeValue(specificDeserializer, decoderContext, type, '{"value":"hello","count":"oops","tags":["x"]}')
        }

        then:
        scalarPathDefaultFailure != null
        scalarPathSpecificFailure != null
        scalarPathDefaultFailure instanceof SerdeException
        scalarPathSpecificFailure instanceof SerdeException
        ((SerdeException) scalarPathDefaultFailure).pathAsString?.contains('count')
        ((SerdeException) scalarPathSpecificFailure).pathAsString?.contains('count')

        cleanup:
        context.close()
    }

    @SuppressWarnings('JsonDuplicatePropertyKeys')
    void 'test record generated deserializer dispatch paths for small and large property sets'() {
        given:
        def context = buildContext('test.SmallDispatchRecord', '''
package test;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Introspected;

@Serdeable
@Introspected
record SmallDispatchRecord(String a, int b, boolean c) {}

@Serdeable
@Introspected
record LargeDispatchRecord(String a, int b, boolean c, long d, double e) {}
''')
        def registry = jsonMapper.serdeRegistry
        def decoderContext = registry.newDecoderContext(Object)

        Class<?> smallType = context.classLoader.loadClass('test.SmallDispatchRecord')
        Class<?> largeType = context.classLoader.loadClass('test.LargeDispatchRecord')
        Argument smallArgument = Argument.of(smallType)
        Argument largeArgument = Argument.of(largeType)

        def smallDeserializer = buildDeserializer(context, smallType)
        def largeDeserializer = buildDeserializer(context, largeType)

        when:
        def small = deserializeValue(smallDeserializer, decoderContext, smallArgument, '{"a":"x","b":7,"c":true}')
        def large = deserializeValue(largeDeserializer, decoderContext, largeArgument, '{"a":"x","b":7,"c":true,"d":9,"e":3.5}')

        then:
        invokeDeclared(small, 'a') == 'x'
        invokeDeclared(small, 'b') == 7
        invokeDeclared(small, 'c')
        invokeDeclared(large, 'a') == 'x'
        invokeDeclared(large, 'b') == 7
        invokeDeclared(large, 'c')
        invokeDeclared(large, 'd') == 9L
        invokeDeclared(large, 'e') == 3.5d

        when:
        def smallDuplicate = captureFailure {
            deserializeValue(smallDeserializer, decoderContext, smallArgument, '{"a":"x","a":"y","b":7,"c":true}')
        }
        def largeDuplicate = captureFailure {
            deserializeValue(largeDeserializer, decoderContext, largeArgument, '{"a":"x","b":7,"c":true,"d":9,"e":3.5,"e":1.0}')
        }

        then:
        smallDuplicate instanceof SerdeException
        largeDuplicate instanceof SerdeException
        smallDuplicate.message?.contains('a')
        largeDuplicate.message?.contains('e')

        cleanup:
        context.close()
    }


    void 'test record sourcegen handles nested geometry lists requiring lazy serializer and deserializer fields'() {
        given:
        def context = buildContext('test.MultiPoint', '''
package test;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Introspected;
import java.util.List;

@Serdeable
@Introspected
public record MultiPoint(List<Point> points) {
    @Serdeable
    @Introspected
    public record Point(double x, double y) {}
}
''')
        Class<?> multiPointType = context.classLoader.loadClass('test.MultiPoint')
        Class<?> pointType = context.classLoader.loadClass('test.MultiPoint$Point')
        def introspections = context.getBean(SerdeIntrospections)
        def metadata = introspections.getSerializableIntrospection(Argument.of(multiPointType)).annotationMetadata
        String serializerClassName = metadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_SERIALIZER_CLASS).orElse(null)
        String deserializerClassName = metadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_DESERIALIZER_CLASS).orElse(null)
        Class<?> serializerClass = context.classLoader.loadClass(serializerClassName)
        Class<?> deserializerClass = context.classLoader.loadClass(deserializerClassName)
        Serializer serializer = (Serializer) serializerClass.getDeclaredConstructor().newInstance()
        Deserializer deserializer = (Deserializer) deserializerClass.getDeclaredConstructor().newInstance()
        def registry = jsonMapper.serdeRegistry
        Serializer.EncoderContext encoderContext = registry.newEncoderContext(Object)
        Deserializer.DecoderContext decoderContext = registry.newDecoderContext(Object)
        def type = Argument.of(multiPointType)
        def pointA = pointType.getDeclaredConstructor(double, double).newInstance(1d, 2d)
        def pointB = pointType.getDeclaredConstructor(double, double).newInstance(3d, 4d)
        def multiPoint = multiPointType.getDeclaredConstructor(List).newInstance([pointA, pointB])
        def jsonFactory = new JsonFactory()
        def output = new ByteArrayOutputStream()

        expect:
        serializerClass.declaredFields*.name.any { it.startsWith('SERIALIZER_') }
        deserializerClass.declaredFields*.name.any { it.startsWith('DESERIALIZER_') }

        when:
        jsonFactory.createGenerator(output).withCloseable { generator ->
            Encoder encoder = JacksonEncoder.create(generator)
            serializer.serialize(encoder, encoderContext, type, multiPoint)
        }
        String json = output.toString('UTF-8')

        def roundTrip
        jsonFactory.createParser(json).withCloseable { parser ->
            Decoder decoder = JacksonDecoder.create(parser, LimitingStream.DEFAULT_LIMITS)
            roundTrip = deserializer.deserialize(decoder, decoderContext, type)
        }

        then:
        json == '{"points":[{"x":1.0,"y":2.0},{"x":3.0,"y":4.0}]}'
        invokeDeclared(roundTrip, 'points').toString() == '[Point[x=1.0, y=2.0], Point[x=3.0, y=4.0]]'

        cleanup:
        context.close()
    }

    private Object buildDeserializer(def context, Class<?> recordType) {
        def introspections = context.getBean(SerdeIntrospections)
        def metadata = introspections.getSerializableIntrospection(Argument.of(recordType)).annotationMetadata
        String deserializerClassName = metadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_DESERIALIZER_CLASS).orElse(null)
        Class<?> deserializerClass = context.classLoader.loadClass(deserializerClassName)
        (Deserializer) deserializerClass.getDeclaredConstructor().newInstance()
    }

    private static Object invokeDeclared(Object target, String methodName) {
        def method = target.getClass().getDeclaredMethod(methodName)
        method.setAccessible(true)
        method.invoke(target)
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
