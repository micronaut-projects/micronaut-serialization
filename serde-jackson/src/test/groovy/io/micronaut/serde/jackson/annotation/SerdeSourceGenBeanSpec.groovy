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
        specificDeserializer.class.name == generatedClassName(beanType, 'Deserializer')

        when:
        def fromNull = jsonMapper.readValue('{"value":"hello","count":null,"tags":["a","b"]}', type)

        then:
        fromNull.value == 'hello'
        fromNull.count == 0
        fromNull.tags.toString() == '[a, b]'

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

        cleanup:
        context.close()
    }

    void 'test bean sourcegen handles properties recursing through collections and other types'() {
        given:
        def context = buildContext('test.SchemaProps', '''
package test;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;
import java.util.Map;

@Serdeable
class SchemaProps {
    private String name;
    private List<SchemaProps> allOf;
    private Map<String, SchemaProps> properties;
    private SchemaHolder holder;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<SchemaProps> getAllOf() { return allOf; }
    public void setAllOf(List<SchemaProps> allOf) { this.allOf = allOf; }
    public Map<String, SchemaProps> getProperties() { return properties; }
    public void setProperties(Map<String, SchemaProps> properties) { this.properties = properties; }
    public SchemaHolder getHolder() { return holder; }
    public void setHolder(SchemaHolder holder) { this.holder = holder; }
}

@Serdeable
class SchemaHolder {
    private SchemaProps schema;

    public SchemaProps getSchema() { return schema; }
    public void setSchema(SchemaProps schema) { this.schema = schema; }
}
''')
        Class<?> schemaType = context.classLoader.loadClass('test.SchemaProps')
        Class<?> holderType = context.classLoader.loadClass('test.SchemaHolder')
        def registry = context.getBean(SerdeRegistry)
        def type = Argument.of(schemaType)
        String json = '{"name":"root","allOf":[{"name":"a"}],"properties":{"p":{"name":"b"}},"holder":{"schema":{"name":"c"}}}'

        expect:
        assertGeneratedSerializer(registry, type)
        assertGeneratedDeserializer(registry, type)
        assertGeneratedSerializer(registry, Argument.of(holderType))
        assertGeneratedDeserializer(registry, Argument.of(holderType))

        when:
        def decoded = jsonMapper.readValue(json, type)

        then:
        decoded.name == 'root'
        decoded.allOf*.name == ['a']
        decoded.properties.p.name == 'b'
        decoded.holder.schema.name == 'c'
        jsonMapper.writeValueAsString(decoded) == json

        cleanup:
        context.close()
    }

    void 'test bean sourcegen handles nested and deeply nested references to the owning type'() {
        given:
        def context = buildContext('test.Outer', '''
package test;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Serdeable
public class Outer {
    private String name;
    private Inner inner;
    private List<List<Outer>> deep;
    private Map<String, List<Outer>> grouped;
    private Optional<Outer> optional = Optional.empty();
    private First first;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Inner getInner() { return inner; }
    public void setInner(Inner inner) { this.inner = inner; }
    public List<List<Outer>> getDeep() { return deep; }
    public void setDeep(List<List<Outer>> deep) { this.deep = deep; }
    public Map<String, List<Outer>> getGrouped() { return grouped; }
    public void setGrouped(Map<String, List<Outer>> grouped) { this.grouped = grouped; }
    public Optional<Outer> getOptional() { return optional; }
    public void setOptional(Optional<Outer> optional) { this.optional = optional; }
    public First getFirst() { return first; }
    public void setFirst(First first) { this.first = first; }

    @Serdeable
    public static class Inner {
        private List<Outer> items;

        public List<Outer> getItems() { return items; }
        public void setItems(List<Outer> items) { this.items = items; }
    }
}

@Serdeable
class First {
    private Second second;

    public Second getSecond() { return second; }
    public void setSecond(Second second) { this.second = second; }
}

@Serdeable
class Second {
    private Outer outer;

    public Outer getOuter() { return outer; }
    public void setOuter(Outer outer) { this.outer = outer; }
}
''')
        Class<?> outerType = context.classLoader.loadClass('test.Outer')
        Class<?> innerType = context.classLoader.loadClass('test.Outer$Inner')
        def registry = context.getBean(SerdeRegistry)
        def type = Argument.of(outerType)
        String json = '{"name":"root","inner":{"items":[{"name":"a"}]},"deep":[[{"name":"b"}]],"grouped":{"g":[{"name":"c"}]},"optional":{"name":"d"},"first":{"second":{"outer":{"name":"e"}}}}'

        expect:
        assertGeneratedSerializer(registry, type)
        assertGeneratedDeserializer(registry, type)
        assertGeneratedSerializer(registry, Argument.of(innerType))
        assertGeneratedDeserializer(registry, Argument.of(innerType))

        when:
        def decoded = jsonMapper.readValue(json, type)

        then:
        decoded.name == 'root'
        decoded.inner.items*.name == ['a']
        decoded.deep*.name == [['b']]
        decoded.grouped.g*.name == ['c']
        decoded.optional.get().name == 'd'
        decoded.first.second.outer.name == 'e'
        jsonMapper.writeValueAsString(decoded) == json

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
