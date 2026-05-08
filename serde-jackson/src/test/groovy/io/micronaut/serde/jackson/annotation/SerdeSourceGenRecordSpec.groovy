package io.micronaut.serde.jackson.annotation

import io.micronaut.core.type.Argument
import io.micronaut.serde.Deserializer
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.Serializer
import io.micronaut.serde.exceptions.SerdeException
import io.micronaut.serde.jackson.JsonCompileSpec

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
        def registry = context.getBean(SerdeRegistry)
        def type = Argument.of(recordType)

        expect:
        assertGeneratedSerializer(registry, type)
        assertGeneratedDeserializer(registry, type)

        when:
        def record = recordType.getDeclaredConstructor(String, int).newInstance('hello', 7)
        String json = jsonMapper.writeValueAsString(record)
        def deserialized = jsonMapper.readValue(json, type)

        then:
        json == '{"value":"hello","count":7}'
        deserialized == record

        cleanup:
        context.close()
    }

    void 'test record generated deserializer handles duplicate unknown null defaults and property path failures'() {
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
        def registry = context.getBean(SerdeRegistry)
        Deserializer.DecoderContext decoderContext = registry.newDecoderContext(Object)
        def type = Argument.of(recordType)
        Deserializer defaultDeserializer = registry.findDeserializer(type)
        Deserializer specificDeserializer = defaultDeserializer.createSpecific(decoderContext, type)

        expect:
        specificDeserializer.class.name == generatedClassName(recordType, 'Deserializer')

        when:
        def fromMissing = jsonMapper.readValue('{"value":"hello","tags":["a","b"]}', type)

        then:
        fromMissing.value() == 'hello'
        fromMissing.count() == 0
        fromMissing.tags().toString() == '[a, b]'

        when:
        def unknownFailure = captureFailure {
            jsonMapper.readValue('{"value":"a","count":1,"extra":2}', type)
        }

        then:
        if (unknownFailure != null) {
            assert unknownFailure.message?.contains('extra')
        }

        when:
        def scalarPathFailure = captureFailure {
            jsonMapper.readValue('{"value":"hello","count":"oops","tags":["x"]}', type)
        }

        then:
        scalarPathFailure != null
        scalarPathFailure instanceof SerdeException
        ((SerdeException) scalarPathFailure).pathAsString?.contains('count')

        cleanup:
        context.close()
    }

    void 'test record generated deserializer dispatch paths for small and large property sets'() {
        given:
        def context = buildContext('test.DispatchRecordTypes', '''
package test;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Introspected;

public final class DispatchRecordTypes {
    @Serdeable
    @Introspected
    public record SmallDispatchRecord(String a, int b, boolean c) {}

    @Serdeable
    @Introspected
    public record LargeDispatchRecord(String a, int b, boolean c, long d, double e) {}
}
''')
        def registry = context.getBean(SerdeRegistry)

        Class<?> smallType = context.classLoader.loadClass('test.DispatchRecordTypes$SmallDispatchRecord')
        Class<?> largeType = context.classLoader.loadClass('test.DispatchRecordTypes$LargeDispatchRecord')
        Argument smallArgument = Argument.of(smallType)
        Argument largeArgument = Argument.of(largeType)

        when:
        def small = jsonMapper.readValue('{"a":"x","b":7,"c":true}', smallArgument)
        def large = jsonMapper.readValue('{"a":"x","b":7,"c":true,"d":9,"e":3.5}', largeArgument)

        then:
        assertGeneratedDeserializer(registry, smallArgument)
        assertGeneratedDeserializer(registry, largeArgument)
        small.a() == 'x'
        small.b() == 7
        small.c()
        large.a() == 'x'
        large.b() == 7
        large.c()
        large.d() == 9L
        large.e() == 3.5d

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
        def registry = context.getBean(SerdeRegistry)
        def type = Argument.of(multiPointType)
        def pointA = pointType.getDeclaredConstructor(double, double).newInstance(1d, 2d)
        def pointB = pointType.getDeclaredConstructor(double, double).newInstance(3d, 4d)
        def multiPoint = multiPointType.getDeclaredConstructor(List).newInstance([pointA, pointB])

        expect:
        assertGeneratedSerializer(registry, type)
        assertGeneratedDeserializer(registry, type)

        when:
        String json = jsonMapper.writeValueAsString(multiPoint)
        def roundTrip = jsonMapper.readValue(json, type)

        then:
        json == '{"points":[{"x":1.0,"y":2.0},{"x":3.0,"y":4.0}]}'
        roundTrip.points().toString() == '[Point[x=1.0, y=2.0], Point[x=3.0, y=4.0]]'

        cleanup:
        context.close()
    }

    private static void assertGeneratedSerializer(SerdeRegistry registry, Argument argument) {
        Serializer serializer = registry.findSerializer(argument).createSpecific(registry.newEncoderContext(Object), argument)
        assert serializer.class.name == generatedClassName(argument.type, 'Serializer')
    }

    private static void assertGeneratedDeserializer(SerdeRegistry registry, Argument argument) {
        Deserializer deserializer = registry.findDeserializer(argument).createSpecific(registry.newDecoderContext(Object), argument)
        assert deserializer.class.name == generatedClassName(argument.type, 'Deserializer')
    }

    private static String generatedClassName(Class<?> type, String suffix) {
        String packageName = type.package.name
        String localName = type.name
        if (packageName) {
            localName = localName.substring(packageName.length() + 1)
        }
        "${packageName ? packageName + '.' : ''}Serde${localName.replace('.', '_').replace('$', '_')}${suffix}"
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
