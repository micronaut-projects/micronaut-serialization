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

class SerdeSourceGenBeanSpec extends JsonCompileSpec {

    void 'test default-constructor bean sourcegen serializer and deserializer are concrete and functional'() {
        given:
        def context = buildContext('test.TestBean', '''
package test;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Introspected;

@Serdeable
@Introspected
public class TestBean {
    private String value;
    private int count;

    public TestBean() {
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }
}
''')
        Class<?> beanType = context.classLoader.loadClass('test.TestBean')
        def introspections = context.getBean(SerdeIntrospections)
        def metadata = introspections.getSerializableIntrospection(Argument.of(beanType)).annotationMetadata
        String serializerClassName = metadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_SERIALIZER_CLASS).orElse(null)
        String deserializerClassName = metadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_DESERIALIZER_CLASS).orElse(null)

        expect:
        metadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_SHAPE).orElse(null) == 'DEFAULT_CONSTRUCTOR_BEAN'
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
        def bean = beanType.getDeclaredConstructor().newInstance()
        beanType.getMethod('setValue', String).invoke(bean, 'hello')
        beanType.getMethod('setCount', int).invoke(bean, 7)
        def type = Argument.of(beanType)

        jsonFactory.createGenerator(output).withCloseable { generator ->
            Encoder encoder = JacksonEncoder.create(generator)
            serializer.serialize(encoder, encoderContext, type, bean)
        }
        String json = output.toString('UTF-8')

        def deserialized
        jsonFactory.createParser(json).withCloseable { parser ->
            Decoder decoder = JacksonDecoder.create(parser, LimitingStream.DEFAULT_LIMITS)
            deserialized = deserializer.deserialize(decoder, decoderContext, type)
        }

        then:
        json == '{"value":"hello","count":7}'
        beanType.getMethod('getValue').invoke(deserialized) == 'hello'
        beanType.getMethod('getCount').invoke(deserialized) == 7

        cleanup:
        context.close()
    }

    void 'test bean generated deserializer shape plus createSpecific parity for duplicate unknown null defaults and property path failures'() {
        given:
        def context = buildContext('test.ParityBean', '''
package test;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Introspected;

@Serdeable
@Introspected
public class ParityBean {
    private String value;
    private int count;
    private java.util.List<String> tags;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public java.util.List<String> getTags() {
        return tags;
    }

    public void setTags(java.util.List<String> tags) {
        this.tags = tags;
    }
}
''')
        Class<?> beanType = context.classLoader.loadClass('test.ParityBean')
        def introspections = context.getBean(SerdeIntrospections)
        def metadata = introspections.getSerializableIntrospection(Argument.of(beanType)).annotationMetadata
        String deserializerClassName = metadata.stringValue(SerdeConfig, SerdeConfig.SOURCEGEN_DESERIALIZER_CLASS).orElse(null)
        Class<?> deserializerClass = context.classLoader.loadClass(deserializerClassName)
        def registry = jsonMapper.serdeRegistry
        Deserializer.DecoderContext decoderContext = registry.newDecoderContext(Object)
        def type = Argument.of(beanType)
        Deserializer defaultDeserializer = (Deserializer) deserializerClass.getDeclaredConstructor().newInstance()
        Deserializer specificDeserializer = defaultDeserializer.createSpecific(decoderContext, type)

        expect:
        deserializerClass.declaredFields*.name.any { it.startsWith('KEY_') }
        deserializerClass.declaredFields*.name.any { it.startsWith('ARGUMENT_') }
        deserializerClass.declaredFields*.name.any { it.startsWith('DESERIALIZER_') }
        specificDeserializer.class == deserializerClass

        when:
        def fromDefaultNull = deserializeValue(defaultDeserializer, decoderContext, type, '{"value":"hello","count":null,"tags":["a","b"]}')
        def fromSpecificNull = deserializeValue(specificDeserializer, decoderContext, type, '{"value":"hello","count":null,"tags":["a","b"]}')

        then:
        beanType.getMethod('getValue').invoke(fromDefaultNull) == 'hello'
        beanType.getMethod('getValue').invoke(fromSpecificNull) == 'hello'
        beanType.getMethod('getCount').invoke(fromDefaultNull) == 0
        beanType.getMethod('getCount').invoke(fromSpecificNull) == 0
        beanType.getMethod('getTags').invoke(fromDefaultNull).toString() == '[a, b]'
        beanType.getMethod('getTags').invoke(fromSpecificNull).toString() == '[a, b]'

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
    void 'test bean generated deserializer dispatch paths for small and large property sets'() {
        given:
        def context = buildContext('test.SmallDispatchBean', '''
package test;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Introspected;

@Serdeable
@Introspected
class SmallDispatchBean {
    private String a;
    private int b;
    private boolean c;

    public String getA() { return a; }
    public void setA(String a) { this.a = a; }
    public int getB() { return b; }
    public void setB(int b) { this.b = b; }
    public boolean isC() { return c; }
    public void setC(boolean c) { this.c = c; }
}

@Serdeable
@Introspected
class LargeDispatchBean {
    private String a;
    private int b;
    private boolean c;
    private long d;
    private double e;

    public String getA() { return a; }
    public void setA(String a) { this.a = a; }
    public int getB() { return b; }
    public void setB(int b) { this.b = b; }
    public boolean isC() { return c; }
    public void setC(boolean c) { this.c = c; }
    public long getD() { return d; }
    public void setD(long d) { this.d = d; }
    public double getE() { return e; }
    public void setE(double e) { this.e = e; }
}
''')
        def registry = jsonMapper.serdeRegistry
        def decoderContext = registry.newDecoderContext(Object)

        Class<?> smallType = context.classLoader.loadClass('test.SmallDispatchBean')
        Class<?> largeType = context.classLoader.loadClass('test.LargeDispatchBean')
        Argument smallArgument = Argument.of(smallType)
        Argument largeArgument = Argument.of(largeType)

        def smallDeserializer = buildDeserializer(context, smallType)
        def largeDeserializer = buildDeserializer(context, largeType)

        when:
        def small = deserializeValue(smallDeserializer, decoderContext, smallArgument, '{"a":"x","b":7,"c":true}')
        def large = deserializeValue(largeDeserializer, decoderContext, largeArgument, '{"a":"x","b":7,"c":true,"d":9,"e":3.5}')

        then:
        invokeDeclared(small, 'getA') == 'x'
        invokeDeclared(small, 'getB') == 7
        invokeDeclared(small, 'isC')
        invokeDeclared(large, 'getA') == 'x'
        invokeDeclared(large, 'getB') == 7
        invokeDeclared(large, 'isC')
        invokeDeclared(large, 'getD') == 9L
        invokeDeclared(large, 'getE') == 3.5d

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

    private Object buildDeserializer(def context, Class<?> beanType) {
        def introspections = context.getBean(SerdeIntrospections)
        def metadata = introspections.getSerializableIntrospection(Argument.of(beanType)).annotationMetadata
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
