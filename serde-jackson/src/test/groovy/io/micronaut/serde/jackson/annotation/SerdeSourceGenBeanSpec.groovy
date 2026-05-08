package io.micronaut.serde.jackson.annotation

import io.micronaut.core.type.Argument
import io.micronaut.serde.Deserializer
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.Serializer
import io.micronaut.serde.exceptions.SerdeException
import io.micronaut.serde.jackson.JsonCompileSpec

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
        def registry = context.getBean(SerdeRegistry)
        def type = Argument.of(beanType)

        expect:
        assertGeneratedSerializer(registry, type)
        assertGeneratedDeserializer(registry, type)

        when:
        def bean = beanType.getDeclaredConstructor().newInstance()
        bean.value = 'hello'
        bean.count = 7
        String json = jsonMapper.writeValueAsString(bean)
        def deserialized = jsonMapper.readValue(json, type)

        then:
        json == '{"value":"hello","count":7}'
        deserialized.value == 'hello'
        deserialized.count == 7

        cleanup:
        context.close()
    }

    void 'test bean generated deserializer handles duplicate unknown null defaults and property path failures'() {
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
''', true, [
            'micronaut.serde.deserialization.fail-on-null-for-primitives': false
        ])
        Class<?> beanType = context.classLoader.loadClass('test.ParityBean')
        def registry = context.getBean(SerdeRegistry)
        Deserializer.DecoderContext decoderContext = registry.newDecoderContext(Object)
        def type = Argument.of(beanType)
        Deserializer defaultDeserializer = registry.findDeserializer(type)
        Deserializer specificDeserializer = defaultDeserializer.createSpecific(decoderContext, type)

        expect:
        defaultDeserializer.class.name == generatedClassName(beanType, 'Deserializer')
        specificDeserializer.class.name == generatedClassName(beanType, 'Deserializer')

        when:
        def fromNull = jsonMapper.readValue('{"value":"hello","count":null,"tags":["a","b"]}', type)

        then:
        fromNull.value == 'hello'
        fromNull.count == 0
        fromNull.tags.toString() == '[a, b]'

        when:
        def duplicate = jsonMapper.readValue('{"value":"a","value":"b","count":1}', type)

        then:
        duplicate.value == 'a'
        duplicate.count == 1

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

    @SuppressWarnings('JsonDuplicatePropertyKeys')
    void 'test bean generated deserializer dispatch paths for small and large property sets'() {
        given:
        def context = buildContext('test.DispatchBeanTypes', '''
package test;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Introspected;

public final class DispatchBeanTypes {
    @Serdeable
    @Introspected
    public static class SmallDispatchBean {
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
    public static class LargeDispatchBean {
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
}
''')
        def registry = context.getBean(SerdeRegistry)

        Class<?> smallType = context.classLoader.loadClass('test.DispatchBeanTypes$SmallDispatchBean')
        Class<?> largeType = context.classLoader.loadClass('test.DispatchBeanTypes$LargeDispatchBean')
        Argument smallArgument = Argument.of(smallType)
        Argument largeArgument = Argument.of(largeType)

        when:
        def small = jsonMapper.readValue('{"a":"x","b":7,"c":true}', smallArgument)
        def large = jsonMapper.readValue('{"a":"x","b":7,"c":true,"d":9,"e":3.5}', largeArgument)

        then:
        assertGeneratedDeserializer(registry, smallArgument)
        assertGeneratedDeserializer(registry, largeArgument)
        small.a == 'x'
        small.b == 7
        small.c
        large.a == 'x'
        large.b == 7
        large.c
        large.d == 9L
        large.e == 3.5d

        when:
        def smallDuplicate = jsonMapper.readValue('{"a":"x","a":"y","b":7,"c":true}', smallArgument)
        def largeDuplicate = jsonMapper.readValue('{"a":"x","b":7,"c":true,"d":9,"e":3.5,"e":1.0}', largeArgument)

        then:
        smallDuplicate.a == 'x'
        smallDuplicate.b == 7
        smallDuplicate.c
        largeDuplicate.a == 'x'
        largeDuplicate.b == 7
        largeDuplicate.c
        largeDuplicate.d == 9L
        largeDuplicate.e == 3.5d

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
