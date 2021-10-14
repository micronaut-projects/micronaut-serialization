package io.micronaut.json.generator

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.json.Deserializer
import io.micronaut.json.Serializer
import jakarta.inject.Provider

import java.lang.reflect.ParameterizedType
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType

class MapperVisitorSpec extends AbstractTypeElementSpec implements SerializerUtils {
    void "generator creates a serializer for jackson annotations"() {
        given:
        def compiled = buildClassLoader('example.Test', '''
package example;

import io.micronaut.json.annotation.SerializableBean;

@SerializableBean
public class Test {
}
''')

        def serializerClass = compiled.loadClass('example.$Test$Serializer')

        expect:
        serializerClass != null
        Serializer.class.isAssignableFrom(serializerClass)
    }

    void "nested beans"() {
        given:
        def compiled = buildClassLoader('example.Test', '''
package example;

import io.micronaut.json.annotation.SerializableBean;

@SerializableBean
class A {
    public B b;
    public String bar;
}

@SerializableBean
class B {
    public String foo;
}
''')

        def a = compiled.loadClass("example.A").newInstance()
        def b = compiled.loadClass("example.B").newInstance()

        a.b = b
        a.bar = "123"
        b.foo = "456"

        def serializerB = (Serializer<?>) compiled.loadClass('example.$B$Serializer').newInstance()
        def serializerA = (Serializer<?>) compiled.loadClass('example.$A$Serializer').newInstance(serializerB)

        def deserializerB = (Deserializer<?>) compiled.loadClass('example.$B$Deserializer').newInstance()
        def deserializerA = (Deserializer<?>) compiled.loadClass('example.$A$Deserializer').newInstance(deserializerB)

        expect:
        serializeToString(serializerB, b) == '{"foo":"456"}'
        serializeToString(serializerA, a) == '{"b":{"foo":"456"},"bar":"123"}'
        deserializeFromString(deserializerB, '{"foo":"456"}').foo == "456"
        deserializeFromString(deserializerA, '{"b":{"foo":"456"},"bar":"123"}').bar == "123"
        deserializeFromString(deserializerA, '{"b":{"foo":"456"},"bar":"123"}').b.foo == "456"
    }

    void "lists"() {
        given:
        def compiled = buildClassLoader('example.Test', '''
package example;

import io.micronaut.json.annotation.SerializableBean;import java.util.List;

@SerializableBean
class Test {
    public List<String> list;
}
''')

        def test = compiled.loadClass("example.Test").newInstance()

        test.list = ['foo', 'bar']

        def serializer = (Serializer<?>) compiled.loadClass('example.$Test$Serializer').newInstance()
        def deserializer = (Deserializer<?>) compiled.loadClass('example.$Test$Deserializer').newInstance()

        expect:
        serializeToString(serializer, test) == '{"list":["foo","bar"]}'
        deserializeFromString(deserializer, '{"list":["foo","bar"]}').list == ['foo', 'bar']
    }

    void "maps"() {
        given:
        def compiled = buildClassLoader('example.Test', '''
package example;

import io.micronaut.json.annotation.SerializableBean;
import java.util.Map;

@SerializableBean
class Test {
    public Map<String, String> map;
}
''')

        def test = compiled.loadClass("example.Test").newInstance()
        test.map = ['foo': 'bar']

        def serializer = (Serializer<?>) compiled.loadClass('example.$Test$Serializer').newInstance()
        def deserializer = (Deserializer<?>) compiled.loadClass('example.$Test$Deserializer').newInstance()

        expect:
        serializeToString(serializer, test) == '{"map":{"foo":"bar"}}'
        deserializeFromString(deserializer, '{"map":{"foo":"bar"}}').map == ['foo': 'bar']
    }

    void "null map values"() {
        given:
        def compiled = buildClassLoader('example.Test', '''
package example;

import io.micronaut.json.annotation.SerializableBean;
import java.util.Map;

@SerializableBean
class Test {
    public Map<String, String> map;
}
''')

        def test = compiled.loadClass("example.Test").newInstance()
        test.map = ['foo': null]

        def serializer = (Serializer<?>) compiled.loadClass('example.$Test$Serializer').newInstance()
        def deserializer = (Deserializer<?>) compiled.loadClass('example.$Test$Deserializer').newInstance()

        expect:
        serializeToString(serializer, test) == '{"map":{"foo":null}}'
        deserializeFromString(deserializer, '{"map":{"foo":null}}').map == ['foo': null]
    }

    void "recursive with proper annotation"() {
        given:
        def compiled = buildClassLoader('example.Test', '''
package example;

import io.micronaut.json.annotation.RecursiveSerialization;
import io.micronaut.json.annotation.SerializableBean;

@SerializableBean
class Test {
    @RecursiveSerialization public Test foo;
}
''')

        def test = compiled.loadClass("example.Test").newInstance()
        test.foo = compiled.loadClass("example.Test").newInstance()

        def providerSer = new Provider() {
            @Override
            Object get() {
                return compiled.loadClass('example.$Test$Serializer').newInstance(this)
            }
        }
        def serializer = providerSer.get()

        def providerDes = new Provider() {
            @Override
            Object get() {
                return compiled.loadClass('example.$Test$Deserializer').newInstance(this)
            }
        }
        def deserializer = providerDes.get()

        expect:
        serializeToString(serializer, test) == '{"foo":{}}'
        deserializeFromString(deserializer, '{"foo":{}}').foo.foo == null
    }

    void "simple recursive without proper annotation gives error"() {
        when:
        buildClassLoader('example.Test', '''
package example;

import io.micronaut.json.annotation.SerializableBean;

@SerializableBean
class Test {
    public Test foo;
}
''')
        then:
        def e = thrown Exception

        expect:
        e.message.contains("Circular dependency")
        e.message.contains("foo")
    }

    void "list recursive without proper annotation gives error"() {
        when:
        buildClassLoader('example.Test', '''
package example;

import io.micronaut.json.annotation.SerializableBean;

@SerializableBean
class Test {
    public Test[] foo;
}
''')
        then:
        def e = thrown Exception

        expect:
        e.message.contains("Circular dependency")
        e.message.contains("foo")
    }

    void "mutually recursive without proper annotation gives error"() {
        when:
        buildClassLoader('example.A', '''
package example;

import io.micronaut.json.annotation.SerializableBean;

@SerializableBean
class A {
    public B b;
}

@SerializableBean
class B {
    public A a;
}
''')
        then:
        def e = thrown Exception

        expect:
        e.message.contains("Circular dependency")
        e.message.contains("A->b->*->a->*")
    }

    void "recursive ref to type with dedicated serializer doesn't error"() {
        when:
        buildClassLoader('example.A', '''
package example;

import io.micronaut.json.annotation.SerializableBean;

@SerializableBean
class A {
    B b;
}
// not annotated
class B {
    A a;
}
''')
        then:
        return
    }

    void "nested generic"() {
        given:
        def compiled = buildClassLoader('example.Test', '''
package example;

import io.micronaut.json.annotation.SerializableBean;

@SerializableBean
class A {
    public B<C> b;
}

@SerializableBean
class B<T> {
    public T foo;
}

@SerializableBean
class C {
    public String bar;
}
''')

        def a = compiled.loadClass("example.A").newInstance()
        def b = compiled.loadClass("example.B").newInstance()
        def c = compiled.loadClass("example.C").newInstance()

        a.b = b
        b.foo = c
        c.bar = "123"

        def serializerC = (Serializer<?>) compiled.loadClass('example.$C$Serializer').newInstance()
        def serializerBClass = compiled.loadClass('example.$B_T_$Serializer')
        def serializerB = (Serializer<?>) serializerBClass.newInstance(serializerC)
        def serializerA = (Serializer<?>) compiled.loadClass('example.$A$Serializer').newInstance(serializerB)

        def genericSerializerParam = serializerBClass.getDeclaredConstructor(Serializer.class).getGenericParameterTypes()[0]

        def deserializerC = (Deserializer<?>) compiled.loadClass('example.$C$Deserializer').newInstance()
        def deserializerBClass = compiled.loadClass('example.$B_T_$Deserializer')
        def deserializerB = (Deserializer<?>) deserializerBClass.newInstance(deserializerC)
        def deserializerA = (Deserializer<?>) compiled.loadClass('example.$A$Deserializer').newInstance(deserializerB)

        def genericDeserializerParam = deserializerBClass.getDeclaredConstructor(Deserializer.class).getGenericParameterTypes()[0]

        expect:
        serializeToString(serializerA, a) == '{"b":{"foo":{"bar":"123"}}}'
        deserializeFromString(deserializerA, '{"b":{"foo":{"bar":"123"}}}').b.foo.bar == "123"

        genericSerializerParam instanceof ParameterizedType
        ((ParameterizedType) genericSerializerParam).actualTypeArguments[0] instanceof WildcardType
        ((ParameterizedType) genericSerializerParam).actualTypeArguments[0].lowerBounds[0] instanceof TypeVariable

        genericDeserializerParam instanceof ParameterizedType
        ((ParameterizedType) genericDeserializerParam).actualTypeArguments[0] instanceof WildcardType
        ((ParameterizedType) genericDeserializerParam).actualTypeArguments[0].upperBounds[0] instanceof TypeVariable
    }

    void "nested generic inline"() {
        given:
        def compiled = buildClassLoader('example.Test', '''
package example;

import io.micronaut.json.annotation.SerializableBean;

@SerializableBean
class A {
    public B<C> b;
}

@SerializableBean(inline = true)
class B<T> {
    public T foo;
}

@SerializableBean
class C {
    public String bar;
}
''')

        def a = compiled.loadClass("example.A").newInstance()
        def b = compiled.loadClass("example.B").newInstance()
        def c = compiled.loadClass("example.C").newInstance()

        a.b = b
        b.foo = c
        c.bar = "123"

        def serializerC = (Serializer<?>) compiled.loadClass('example.$C$Serializer').newInstance()
        def serializerA = (Serializer<?>) compiled.loadClass('example.$A$Serializer').newInstance(serializerC)
        def deserializerC = (Deserializer<?>) compiled.loadClass('example.$C$Deserializer').newInstance()
        def deserializerA = (Deserializer<?>) compiled.loadClass('example.$A$Deserializer').newInstance(deserializerC)

        expect:
        serializeToString(serializerA, a) == '{"b":{"foo":{"bar":"123"}}}'
        deserializeFromString(deserializerA, '{"b":{"foo":{"bar":"123"}}}').b.foo.bar == "123"
    }

    void "enum"() {
        given:
        def compiled = buildClassLoader('example.Test', '''
package example;

import io.micronaut.json.annotation.SerializableBean;

@SerializableBean
class A {
    public E e;
}

enum E {
    A, B
}
''')

        def a = compiled.loadClass("example.A").newInstance()
        a.e = compiled.loadClass("example.E").enumConstants[1]

        def serializerA = (Serializer<?>) compiled.loadClass('example.$A$Serializer').newInstance()
        def deserializerA = (Deserializer<?>) compiled.loadClass('example.$A$Deserializer').newInstance()

        expect:
        serializeToString(serializerA, a) == '{"e":"B"}'
        deserializeFromString(deserializerA, '{"e":"A"}').e.name() == 'A'
        deserializeFromString(deserializerA, '{"e":"B"}').e.name() == 'B'
    }

    void "nested class"() {
        given:
        def compiled = buildClassLoader('example.Test', '''
package example;

import io.micronaut.json.annotation.SerializableBean;

class A {
    @SerializableBean
    static class B {
    }
}
''')

        def b = compiled.loadClass('example.A$B').newInstance()
        def serializerB = (Serializer<?>) compiled.loadClass('example.$A_B$Serializer').newInstance()

        expect:
        serializeToString(serializerB, b) == '{}'
    }

    void "interface"() {
        given:
        def compiled = buildClassLoader('example.Test', '''
package example;

import io.micronaut.json.annotation.SerializableBean;

@SerializableBean(allowDeserialization = false)
interface Test {
    String getFoo();
}
''')
        def testBean = ['getFoo': { Object[] args -> 'bar' }].asType(compiled.loadClass('example.Test'))
        def serializer = compiled.loadClass('example.$Test$Serializer').newInstance()

        expect:
        serializeToString(serializer, testBean) == '{"foo":"bar"}'

        when:
        compiled.loadClass('example.$Test$Deserializer')

        then:
        thrown ClassNotFoundException
    }

    void "optional"() {
        given:
        def compiled = buildClassLoader('example.A', '''
package example;

import io.micronaut.json.annotation.SerializableBean;

@SerializableBean
class A {
    public java.util.Optional<B> b;
}

@SerializableBean
class B {
}
''')
        def testBean = compiled.loadClass("example.A").newInstance()
        testBean.b = Optional.of(compiled.loadClass("example.B").newInstance())

        def serializerB = compiled.loadClass('example.$B$Serializer').newInstance()
        def serializer = compiled.loadClass('example.$A$Serializer').newInstance(serializerB)

        def deserializerB = compiled.loadClass('example.$B$Deserializer').newInstance()
        def deserializer = compiled.loadClass('example.$A$Deserializer').newInstance(deserializerB)

        expect:
        serializeToString(serializer, testBean) == '{"b":{}}'
        deserializeFromString(deserializer, '{"b":{}}').b.isPresent()
    }

    void "generic injection collision"() {
        given:
        def ctx = buildContext('example.A', '''
package example;

import io.micronaut.json.annotation.SerializableBean;

@SerializableBean
class A {
}

@SerializableBean
class B extends A {
}

@SerializableBean
class C {
    A a;
}
''', true)

        expect:
        ctx.getBeansOfType(Serializer.Factory).any { it.genericType == ctx.classLoader.loadClass('example.C') }
    }

    void "disabled mode isn't generated"() {
        given:
        def ctx = buildContext('example.A', '''
package example;

import io.micronaut.json.annotation.SerializableBean;

@SerializableBean(allowDeserialization = false)
class A {
}
''', true)

        expect:
        ctx.getBeansOfType(Serializer.Factory).any { it.genericType == ctx.classLoader.loadClass('example.A') }
        !ctx.getBeansOfType(Deserializer.Factory).any { it.genericType == ctx.classLoader.loadClass('example.A') }
    }

    void "enum generated"() {
        given:
        def ctx = buildContext('example.Foo', '''
package example;

import io.micronaut.json.annotation.SerializableBean;

@SerializableBean
enum Foo {
    A, B
}
''', true)

        def serializer = ctx.getBeansOfType(Serializer.Factory)
                .find { it.genericType == ctx.classLoader.loadClass('example.Foo') }
                .newInstance(null, null)

        expect:
        serializeToString(serializer, Enum.valueOf(ctx.classLoader.loadClass('example.Foo'), 'A')) == '"A"'
    }

    void "injected serializer uses Serializer.isEmpty"() {
        given:
        def ctx = buildContext('example.A', '''
package example;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.json.Encoder;
import io.micronaut.json.annotation.SerializableBean;
import jakarta.inject.Singleton;

@SerializableBean(allowDeserialization = false)
class A {
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public B b;
}

class B {
    public boolean present;
}

@Singleton
class BSerializer implements io.micronaut.json.Serializer<B> {
    @Override
    public void serialize(Encoder encoder, B value) throws java.io.IOException {
        encoder.encodeBoolean(value.present);
    }

    @Override
    public boolean isEmpty(B value) {
        return !value.present;
    }
}
''', true)
        def bSerializer = ctx.getBean(ctx.classLoader.loadClass('example.BSerializer'))
        def aSerializer = ctx.classLoader.loadClass('example.$A$Serializer').newInstance(bSerializer)

        def bPresent = ctx.classLoader.loadClass('example.B').newInstance()
        bPresent.present = true
        def bAbsent = ctx.classLoader.loadClass('example.B').newInstance()
        bAbsent.present = false

        def aPresent = ctx.classLoader.loadClass('example.A').newInstance()
        aPresent.b = bPresent
        def aAbsent = ctx.classLoader.loadClass('example.A').newInstance()
        aAbsent.b = bAbsent

        expect:
        serializeToString(aSerializer, aPresent) == '{"b":true}'
        serializeToString(aSerializer, aAbsent) == '{}'
    }

    void "mixin visitor"() {
        given:
        def ctx = buildContext('example.A', '''
package example;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.json.annotation.SerializableBean;
import io.micronaut.json.annotation.SerializationMixin;
import jakarta.inject.Singleton;

@SerializationMixin(forClass = A.class)
class Marker {}

class A {
    String foo;
}
''', true)

        expect:
        ctx.getBeansOfType(Serializer.Factory).any { it.genericType == ctx.classLoader.loadClass('example.A') }
    }

    def 'simple views'() {
        given:
        def ctx = buildContext('example.WithViews', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.json.annotation.SerializableBean;

@SerializableBean
@JsonView(Public.class)
class WithViews {
    public String firstName;
    public String lastName;
    @JsonView(Internal.class)
    public String birthdate;
    @JsonView(Admin.class)
    public String password; // don't do plaintext passwords at home please
}

class Public {}

class Internal extends Public {}

class Admin extends Internal {}
''', true)
        def serializer = ctx.classLoader.loadClass('example.$WithViews$Serializer').newInstance()
        def deserializer = ctx.classLoader.loadClass('example.$WithViews$Deserializer').newInstance()
        def withViews = ctx.classLoader.loadClass('example.WithViews').newInstance()
        withViews.firstName = 'Bob'
        withViews.lastName = 'Jones'
        withViews.birthdate = '08/01/1980'
        withViews.password = 'secret'

        def viewPublic = ctx.classLoader.loadClass('example.Public')
        def viewInternal = ctx.classLoader.loadClass('example.Internal')
        def viewAdmin = ctx.classLoader.loadClass('example.Admin')

        expect:
        serializeToString(serializer, withViews, viewAdmin) ==
                '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}'
        serializeToString(serializer, withViews, viewInternal) ==
                '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980"}'
        serializeToString(serializer, withViews, viewPublic) ==
                '{"firstName":"Bob","lastName":"Jones"}'
        serializeToString(serializer, withViews) == '{}'

        deserializeFromString(deserializer, '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}')
                .firstName == null

        deserializeFromString(deserializer, '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', viewPublic)
                .firstName == 'Bob'
        deserializeFromString(deserializer, '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', viewPublic)
                .birthdate == null

        deserializeFromString(deserializer, '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', viewInternal)
                .firstName == 'Bob'
        deserializeFromString(deserializer, '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', viewInternal)
                .birthdate == '08/01/1980'
        deserializeFromString(deserializer, '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', viewInternal)
                .password == null

        deserializeFromString(deserializer, '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', viewAdmin)
                .firstName == 'Bob'
        deserializeFromString(deserializer, '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', viewAdmin)
                .birthdate == '08/01/1980'
        deserializeFromString(deserializer, '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', viewAdmin)
                .password == 'secret'
    }

    def 'unwrapped view'() {
        given:
        def ctx = buildContext('example.WithViews', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.json.annotation.SerializableBean;

@SerializableBean
class Outer {
    public String a;
    @JsonView(Runnable.class) @JsonUnwrapped public Nested nested;
}

class Nested {
    public String b;
}
''', true)
        def serializer = ctx.classLoader.loadClass('example.$Outer$Serializer').newInstance()
        def deserializer = ctx.classLoader.loadClass('example.$Outer$Deserializer').newInstance()
        def outer = ctx.classLoader.loadClass('example.Outer').newInstance()
        outer.a = 'a'
        outer.nested = ctx.classLoader.loadClass('example.Nested').newInstance()
        outer.nested.b = 'b'

        expect:
        serializeToString(serializer, outer) == '{"a":"a"}'
        // abuse Runnable as the view class
        serializeToString(serializer, outer, Runnable) == '{"a":"a","b":"b"}'

        deserializeFromString(deserializer, '{"a":"a","b":"b"}').nested.b == null
        deserializeFromString(deserializer, '{"a":"a","b":"b"}', Runnable).nested.b == 'b'
    }

    def 'custom serializer'() {
        def ctx = buildContext('example.Test', '''
package example;

import io.micronaut.context.annotation.Bean;import io.micronaut.json.*;
import io.micronaut.json.annotation.*;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.Locale;

@SerializableBean
class Test {
    public String foo;
    @CustomSerializer(serializer = UpperCaseSer.class, deserializer = LowerCaseDeser.class)
    public String bar;
}

@Singleton
@Bean(typed = UpperCaseSer.class)
class UpperCaseSer implements Serializer<String> {
    @Override
    public void serialize(Encoder encoder, String value) throws IOException {
        encoder.encodeString(value.toUpperCase(Locale.ROOT));
    }

    @Override
    public boolean isEmpty(String value) {
        return value.isEmpty();
    }
}

@Singleton
@Bean(typed = LowerCaseDeser.class)
class LowerCaseDeser implements Deserializer<String> {
    @Override
    public String deserialize(Decoder decoder) throws IOException {
        return decoder.decodeString().toLowerCase(Locale.ROOT);
    }
}
''', true)
        def serializerFactory = (Serializer.Factory) ctx.createBean(ctx.classLoader.loadClass('example.$Test$Serializer$FactoryImpl'))
        def deserializerFactory = (Deserializer.Factory) ctx.createBean(ctx.classLoader.loadClass('example.$Test$Deserializer$FactoryImpl'))

        def serializer = serializerFactory.newInstance(null, null)
        def deserializer = deserializerFactory.newInstance(null, null)

        def testInstance = ctx.classLoader.loadClass('example.Test').newInstance()

        when:
        testInstance.foo = 'boo'
        testInstance.bar = 'Baz'
        then:'normal ser'
        serializeToString(serializer, testInstance) == '{"foo":"boo","bar":"BAZ"}'

        when:
        testInstance.foo = 'boo'
        testInstance.bar = ''
        then:'empty ser is skipped'
        serializeToString(serializer, testInstance) == '{"foo":"boo"}'

        when:
        testInstance.foo = 'boo'
        testInstance.bar = null
        then:'null ser is skipped'
        serializeToString(serializer, testInstance) == '{"foo":"boo"}'

        expect:'deser'
        deserializeFromString(deserializer, '{"foo":"boo","bar":"BAZ"}').foo == 'boo'
        deserializeFromString(deserializer, '{"foo":"boo","bar":"BAZ"}').bar == 'baz'
        deserializeFromString(deserializer, '{"foo":"boo","bar":null}').bar == null
    }
}
