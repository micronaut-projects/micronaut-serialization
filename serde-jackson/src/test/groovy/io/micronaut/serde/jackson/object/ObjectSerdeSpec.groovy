package io.micronaut.serde.jackson.object

import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.exceptions.SerdeException
import io.micronaut.serde.jackson.JsonCompileSpec
import jakarta.inject.Provider
import org.intellij.lang.annotations.Language
import spock.lang.PendingFeature

import java.lang.reflect.ParameterizedType
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import java.nio.charset.StandardCharsets

class ObjectSerdeSpec extends JsonCompileSpec {
    private <T> String serializeToString(JsonMapper jsonMapper, T value, Class<?> view = Object.class) {
        return new String(jsonMapper.cloneWithViewClass(view).writeValueAsBytes(value), StandardCharsets.UTF_8)
    }

    static <T> T deserializeFromString(JsonMapper jsonMapper, Class<T> type, @Language("json") String json, Class<?> view = Object.class) {
        return jsonMapper.cloneWithViewClass(view).readValue(json, Argument.of(type))
    }

    //region JsonSubTypesSpec
    @PendingFeature(reason = "Support for WRAPPER_ARRAY not implemented yet")
    def 'test JsonSubTypes with wrapper array'() {
        given:
        def compiled = buildContext('example.Base', '''
package example;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonSubTypes({
    @JsonSubTypes.Type(value = A.class, name = "a"),
    @JsonSubTypes.Type(value = B.class, names = {"b", "c"})
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_ARRAY)
class Base {
}

class A extends Base {
    public String fieldA;
}
class B extends Base {
    public String fieldB;
}
''', true)
        def baseClass = compiled.classLoader.loadClass('example.Base')
        def a = newInstance(compiled, 'example.A')
        a.fieldA = 'foo'

        expect:
        deserializeFromString(jsonMapper, baseClass, '["a",{"fieldA":"foo"}]').fieldA == 'foo'
        deserializeFromString(jsonMapper, baseClass, '["b",{"fieldB":"foo"}]').fieldB == 'foo'
        deserializeFromString(jsonMapper, baseClass, '["c",{"fieldB":"foo"}]').fieldB == 'foo'

        serializeToString(jsonMapper, a) == '["a",{"fieldA":"foo"}]'
    }

    @PendingFeature(reason = "Support for @JsonSubTypes not implemented yet")
    def 'test JsonSubTypes with wrapper object'() {
        given:
        def compiled = buildContext('example.Base', '''
package example;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonSubTypes({
    @JsonSubTypes.Type(value = A.class, name = "a"),
    @JsonSubTypes.Type(value = B.class, names = {"b", "c"})
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
class Base {
}

@Serdeable
class A extends Base {
    public String fieldA;
}

@Serdeable
class B extends Base {
    public String fieldB;
}
''')

        def baseClass = compiled.classLoader.loadClass('example.Base')
        def a = newInstance(compiled, 'example.A')
        a.fieldA = 'foo'

        expect:
        deserializeFromString(jsonMapper, baseClass, '{"a":{"fieldA":"foo"}}').fieldA == 'foo'
        deserializeFromString(jsonMapper, baseClass, '{"b":{"fieldB":"foo"}}').fieldB == 'foo'
        deserializeFromString(jsonMapper, baseClass, '{"c":{"fieldB":"foo"}}').fieldB == 'foo'

        serializeToString(jsonMapper, a) == '{"a":{"fieldA":"foo"}}'

        cleanup:
        compiled.close()
    }

    @PendingFeature(reason = "Support for @JsonSubTypes not implemented yet")
    def 'test JsonSubTypes with property'() {
        given:
        def compiled = buildContext('example.Base', '''
package example;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonSubTypes({
    @JsonSubTypes.Type(value = A.class, name = "a"),
    @JsonSubTypes.Type(value = B.class, names = {"b", "c"})
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
class Base {
}

class A extends Base {
    public String fieldA;
}
class B extends Base {
    public String fieldB;
}
''', true)
        def baseClass = compiled.classLoader.loadClass('example.Base')
        def a = newInstance(compiled, 'example.A')
        a.fieldA = 'foo'

        expect:
        deserializeFromString(jsonMapper, baseClass, '{"type":"a","fieldA":"foo"}').fieldA == 'foo'
        deserializeFromString(jsonMapper, baseClass, '{"type":"b","fieldB":"foo"}').fieldB == 'foo'
        deserializeFromString(jsonMapper, baseClass, '{"type":"c","fieldB":"foo"}').fieldB == 'foo'

        serializeToString(jsonMapper, a) == '{"type":"a","fieldA":"foo"}'

        cleanup:
        compiled.close()
    }

    @PendingFeature(reason = "JsonTypeInfo.Id.DEDUCTION not supported")
    def 'test JsonSubTypes with deduction'() {
        given:
        def compiled = buildContext('example.Base', '''
package example;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonSubTypes({
    @JsonSubTypes.Type(value = A.class),
    @JsonSubTypes.Type(value = B.class)
})
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
class Base {
}

class A extends Base {
    public String fieldA;
}
class B extends Base {
    public String fieldB;
}
''', true)
        def baseClass = compiled.classLoader.loadClass('example.Base')
        def a = newInstance(compiled, 'example.A')
        a.fieldA = 'foo'

        expect:
        deserializeFromString(jsonMapper, baseClass, '{"fieldA":"foo"}').fieldA == 'foo'
        deserializeFromString(jsonMapper, baseClass, '{"fieldB":"foo"}').fieldB == 'foo'

        serializeToString(jsonMapper, a) == '{"fieldA":"foo"}'

        cleanup:
        compiled.close()
    }

    @PendingFeature(reason = "JsonTypeInfo.Id.DEDUCTION not supported")
    def 'test JsonSubTypes with deduction with supertype prop'() {
        given:
        def compiled = buildContext('example.Base', '''
package example;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonSubTypes({
    @JsonSubTypes.Type(value = A.class),
    @JsonSubTypes.Type(value = B.class)
})
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
class Base {
    public String sup;
}

class A extends Base {
    public String fieldA;
}
class B extends Base {
    public String fieldB;
}
''', true)
        def baseClass = compiled.classLoader.loadClass('example.Base')
        def a = newInstance(compiled, 'example.A')
        a.sup = 'x'
        a.fieldA = 'foo'

        expect:
        deserializeFromString(jsonMapper, baseClass, '{"sup":"x","fieldA":"foo"}').sup == 'x'
        deserializeFromString(jsonMapper, baseClass, '{"sup":"x","fieldA":"foo"}').fieldA == 'foo'
        deserializeFromString(jsonMapper, baseClass, '{"sup":"x","fieldB":"foo"}').sup == 'x'
        deserializeFromString(jsonMapper, baseClass, '{"sup":"x","fieldB":"foo"}').fieldB == 'foo'

        serializeToString(jsonMapper, a) == '{"fieldA":"foo","sup":"x"}'

        cleanup:
        compiled.close()
    }

    @PendingFeature(reason = "JsonTypeInfo.Id.DEDUCTION not supported")
    def 'test JsonSubTypes with deduction unwrapped'() {
        given:
        def compiled = buildContext('example.Base', '''
package example;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonSubTypes({
    @JsonSubTypes.Type(value = A1.class),
    @JsonSubTypes.Type(value = B1.class)
})
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
class Base1 {
    @JsonUnwrapped public Base2 base2;
}

class A1 extends Base1 {
    public String fieldA1;
}
class B1 extends Base1 {
    public String fieldB1;
}

@JsonSubTypes({
    @JsonSubTypes.Type(value = A2.class),
    @JsonSubTypes.Type(value = B2.class)
})
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
class Base2 {
    public String sup;
}

class A2 extends Base2 {
    public String fieldA2;
}
class B2 extends Base2 {
    public String fieldB2;
}
''', true)
        def baseClass = compiled.classLoader.loadClass('example.Base1')
        def parsed = deserializeFromString(jsonMapper, baseClass, '{"fieldA1":"foo","sup":"x","fieldA2":"bar"}')

        def a1 = newInstance(compiled, 'example.A1')
        a1.fieldA1 = 'foo'
        def a2 = newInstance(compiled, 'example.A2')
        a2.sup = 'x'
        a2.fieldA2 = 'bar'
        a1.base2 = a2

        expect:
        parsed.fieldA1 == 'foo'
        parsed.base2.sup == 'x'
        parsed.base2.fieldA2 == 'bar'

        serializeToString(jsonMapper, a1) == '{"fieldA1":"foo","fieldA2":"bar","sup":"x"}'

        cleanup:
        compiled.close()
    }

    @PendingFeature(reason = "@JsonIgnoreProperties(ignoreUnknown = true) not yet implemented")
    void 'test @JsonIgnoreProperties unknown property handling on subtypes'() {
        given:
        def compiled = buildContext('example.Base', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonSubTypes({
    @JsonSubTypes.Type(value = A.class),
    @JsonSubTypes.Type(value = B.class)
})
@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS, include = JsonTypeInfo.As.PROPERTY, property = "type")
class Base {
}
@JsonIgnoreProperties(ignoreUnknown = true)
class A extends Base {
}
@JsonIgnoreProperties(ignoreUnknown = false)
class B extends Base {
}
''', true)
        def baseClass = compiled.classLoader.loadClass('example.Base')

        expect:
        deserializeFromString(jsonMapper, baseClass, '{"type":".A","foo":"bar"}').class.simpleName == 'A'

        when:
        deserializeFromString(jsonMapper, baseClass, '{"type":".B","foo":"bar"}')
        then:
        thrown SerdeException

        cleanup:
        compiled.close()
    }

    void 'test @JsonSubTypes with @AnySetter'() {
        given:
        def compiled = buildContext('example.Base', '''
package example;

import com.fasterxml.jackson.annotation.*;
import java.util.*;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonSubTypes({
    @JsonSubTypes.Type(value = A.class),
    @JsonSubTypes.Type(value = B.class)
})
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "type")
class Base {
}

@Serdeable
class A extends Base {
    private Map<String, String> anySetter = new HashMap<>();
    
    @JsonAnySetter
    void put(String key, String value) {
        anySetter.put(key, value);
    }
}

@Serdeable
class B extends Base {
    private Map<String, String> anySetter = new HashMap<>();
    
    @JsonAnySetter
    void put(String key, String value) {
        anySetter.put(key, value);
    }
}
''', true)
        def baseClass = compiled.classLoader.loadClass('example.Base')

        expect:
        deserializeFromString(jsonMapper, baseClass, '{"type":"example.A","foo":"bar"}').class.simpleName == 'A'
        deserializeFromString(jsonMapper, baseClass, '{"type":"example.A","foo":"bar"}').anySetter == [foo: 'bar']
        deserializeFromString(jsonMapper, baseClass, '{"type":"example.B","foo":"bar"}').class.simpleName == 'B'
        deserializeFromString(jsonMapper, baseClass, '{"type":"example.B","foo":"bar"}').anySetter == [foo: 'bar']

        deserializeFromString(jsonMapper, baseClass, '{"foo":"bar","type":"example.A"}').anySetter == [foo: 'bar']
        deserializeFromString(jsonMapper, baseClass, '{"foo":"bar","type":"example.B"}').anySetter == [foo: 'bar']

        cleanup:
        compiled.close()
    }

    @PendingFeature(reason = "Support for WRAPPER_ARRAY is not yet implemented")
    def 'test @JsonSubTypes with @JsonTypeName'() {
        given:
        def compiled = buildContext('example.Base', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonSubTypes({
    @JsonSubTypes.Type(A.class),
    @JsonSubTypes.Type(B.class)
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_ARRAY)
class Base {
}

@JsonTypeName
class A extends Base {
    public String fieldA;
}
@JsonTypeName("b")
class B extends Base {
    public String fieldB;
}
''', true)
        def jsonMapper = compiled.getBean(JsonMapper)
        def baseClass = compiled.classLoader.loadClass('example.Base')
        def a = compiled.classLoader.loadClass('example.A').newInstance()
        a.fieldA = 'foo'

        expect:
        deserializeFromString(jsonMapper, baseClass, '["A",{"fieldA":"foo"}]').fieldA == 'foo'
        deserializeFromString(jsonMapper, baseClass, '["b",{"fieldB":"foo"}]').fieldB == 'foo'

        serializeToString(jsonMapper, a) == '["A",{"fieldA":"foo"}]'

        cleanup:
        compiled.close()
    }

    void "test nested beans"() {
        given:
        def compiled = buildContext('example.Test', '''
package example;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class A {
    public B b;
    public String bar;
}

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class B {
    public String foo;
}
''', true)

        def a = newInstance(compiled, 'example.A')
        def b = newInstance(compiled, 'example.B')
        def aClass = a.getClass()
        def bClass = b.getClass()

        a.b = b
        a.bar = "123"
        b.foo = "456"

        expect:
        serializeToString(jsonMapper, b) == '{"foo":"456"}'
        serializeToString(jsonMapper, a) == '{"b":{"foo":"456"},"bar":"123"}'
        deserializeFromString(jsonMapper, bClass, '{"foo":"456"}').foo == "456"
        deserializeFromString(jsonMapper, aClass, '{"b":{"foo":"456"},"bar":"123"}').bar == "123"
        deserializeFromString(jsonMapper, aClass, '{"b":{"foo":"456"},"bar":"123"}').b.foo == "456"

        cleanup:
        compiled.close()
    }

    void "test lists"() {
        given:
        def compiled = buildContext('example.Test', '''
package example;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    public List<String> list;
}
''', true)
        def test = newInstance(compiled, 'example.Test')
        test.list = ['foo', 'bar']

        def testClass = test.getClass()

        expect:
        serializeToString(jsonMapper, test) == '{"list":["foo","bar"]}'
        deserializeFromString(jsonMapper, testClass, '{"list":["foo","bar"]}').list == ['foo', 'bar']

        cleanup:
        compiled.close()
    }

    void "test maps"() {
        given:
        def compiled = buildContext('example.Test', '''
package example;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import java.util.Map;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    public Map<String, String> map;
}
''', true)
        def test = newInstance(compiled, 'example.Test')
        test.map = ['foo': 'bar']
        def testClass = test.getClass()

        expect:
        serializeToString(jsonMapper, test) == '{"map":{"foo":"bar"}}'
        deserializeFromString(jsonMapper, testClass, '{"map":{"foo":"bar"}}').map == ['foo': 'bar']

        cleanup:
        compiled.close()
    }

    void "test null map values"() {
        given:
        def compiled = buildContext('example.Test', '''
package example;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import java.util.Map;

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@Serdeable
class Test {
    public Map<String, String> map;
}
''', true)
        def test = newInstance(compiled, 'example.Test')
        test.map = ['foo': null]
        def testClass = test.getClass()

        expect:
        serializeToString(jsonMapper, test) == '{"map":{"foo":null}}'
        deserializeFromString(jsonMapper, testClass, '{"map":{"foo":null}}').map == ['foo': null]

        cleanup:
        compiled.close()
    }

    void "test nested generic"() {
        given:
        def compiled = buildContext('example.Test', '''
package example;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@Serdeable
class A {
    public B<C> b;
}

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@Serdeable
class B<T> {
    public T foo;
}

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@Serdeable
class C {
    public String bar;
}
''', true)
        def jsonMapper = compiled.getBean(JsonMapper)

        def a = newInstance(compiled, 'example.A')
        def b = newInstance(compiled, 'example.B')
        def c = newInstance(compiled, 'example.C')
        def aClass = a.getClass()
        a.b = b
        b.foo = c
        c.bar = "123"

        expect:
        serializeToString(jsonMapper, a) == '{"b":{"foo":{"bar":"123"}}}'
        deserializeFromString(jsonMapper, aClass, '{"b":{"foo":{"bar":"123"}}}').b.foo.bar == "123"

        cleanup:
        compiled.close()
    }

    void "test nested generic inline"() {
        given:
        def compiled = buildContext('example.Test', '''
package example;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class A {
    public B<C> b;
}

@Serdeable//(inline = true)
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class B<T> {
    public T foo;
}

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class C {
    public String bar;
}
''', true)
        def jsonMapper = compiled.getBean(JsonMapper)

        def a = newInstance(compiled, 'example.A')
        def b = newInstance(compiled, 'example.B')
        def c = newInstance(compiled, 'example.C')
        def aClass = a.getClass()

        a.b = b
        b.foo = c
        c.bar = "123"

        expect:
        serializeToString(jsonMapper, a) == '{"b":{"foo":{"bar":"123"}}}'
        deserializeFromString(jsonMapper, aClass, '{"b":{"foo":{"bar":"123"}}}').b.foo.bar == "123"

        cleanup:
        compiled.close()
    }

    void "test enum fields"() {
        given:
        def compiled = buildContext('example.Test', '''
package example;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class A {
    public E e;
}

enum E {
    A, B
}
''', true)
        def a = newInstance(compiled, 'example.A')
        def aClass = a.getClass()
        a.e = compiled.classLoader.loadClass("example.E").enumConstants[1]

        expect:
        serializeToString(jsonMapper, a) == '{"e":"B"}'
        deserializeFromString(jsonMapper, aClass, '{"e":"A"}').e.name() == 'A'
        deserializeFromString(jsonMapper, aClass, '{"e":"B"}').e.name() == 'B'

        cleanup:
        compiled.close()
    }

    void "test nested class"() {
        given:
        def compiled = buildContext('example.Test', '''
package example;

import io.micronaut.serde.annotation.Serdeable;

class A {
    @Serdeable
    static class B {
    }
}
''', true)
        def b = newInstance(compiled, 'example.A$B')

        expect:
        serializeToString(jsonMapper, b) == '{}'
    }

    void "test interface"() {
        given:
        def compiled = buildContext('example.Test', '''
package example;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable//(allowDeserialization = false)
interface Test {
    String getFoo();
}
''', true)
        def jsonMapper = compiled.getBean(JsonMapper)
        def testBean = ['getFoo': { Object[] args -> 'bar' }].asType(compiled.classLoader.loadClass('example.Test'))

        expect:
        serializeToString(jsonMapper, testBean) == '{"foo":"bar"}'

        cleanup:
        compiled.close()
    }

    void "test optional"() {
        given:
        def compiled = buildContext('example.A', '''
package example;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class A {
    public java.util.Optional<B> b;
}

@Serdeable
class B {
}
''', true)
        def testBean = newInstance(compiled, 'example.A')
        testBean.b = Optional.of(newInstance(compiled, 'example.B'))

        expect:
        serializeToString(jsonMapper, testBean) == '{"b":{}}'
        deserializeFromString(jsonMapper, compiled.classLoader.loadClass("example.A"), '{"b":{}}').b.isPresent()
    }

    void "enum generated"() {
        given:
        def ctx = buildContext('example.Foo', '''
package example;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
enum Foo {
    A, B
}
''', true)
        def jsonMapper = ctx.getBean(JsonMapper)

        expect:
        serializeToString(jsonMapper, Enum.valueOf(ctx.classLoader.loadClass('example.Foo'), 'A')) == '"A"'
    }

    @PendingFeature
    void "injected serializer uses Serializer.isEmpty"() {
        given:
        def ctx = buildContext('example.A', '''
package example;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.json.Encoder;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;

@Serdeable//(allowDeserialization = false)
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
        def jsonMapper = compiled.getBean(JsonMapper)

        def bPresent = ctx.classLoader.loadClass('example.B').newInstance()
        bPresent.present = true
        def bAbsent = ctx.classLoader.loadClass('example.B').newInstance()
        bAbsent.present = false

        def aPresent = ctx.classLoader.loadClass('example.A').newInstance()
        aPresent.b = bPresent
        def aAbsent = ctx.classLoader.loadClass('example.A').newInstance()
        aAbsent.b = bAbsent

        expect:
        serializeToString(jsonMapper, aPresent) == '{"b":true}'
        serializeToString(jsonMapper, aAbsent) == '{}'
    }

    @PendingFeature(reason = "Support for @JsonView not yet implemented")
    def 'simple views'() {
        given:
        def ctx = buildContext('example.WithViews', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
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
        def jsonMapper = ctx.getBean(JsonMapper)

        def withViews = ctx.classLoader.loadClass('example.WithViews').newInstance()
        withViews.firstName = 'Bob'
        withViews.lastName = 'Jones'
        withViews.birthdate = '08/01/1980'
        withViews.password = 'secret'

        def viewPublic = ctx.classLoader.loadClass('example.Public')
        def viewInternal = ctx.classLoader.loadClass('example.Internal')
        def viewAdmin = ctx.classLoader.loadClass('example.Admin')

        expect:
        serializeToString(jsonMapper, withViews, viewAdmin) ==
                '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}'
        serializeToString(jsonMapper, withViews, viewInternal) ==
                '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980"}'
        serializeToString(jsonMapper, withViews, viewPublic) ==
                '{"firstName":"Bob","lastName":"Jones"}'
        serializeToString(jsonMapper, withViews) == '{}'

        deserializeFromString(jsonMapper, ctx.classLoader.loadClass("example.WithViews"), '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}')
                .firstName == null

        deserializeFromString(jsonMapper, ctx.classLoader.loadClass("example.WithViews"), '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', viewPublic)
                .firstName == 'Bob'
        deserializeFromString(jsonMapper, ctx.classLoader.loadClass("example.WithViews"), '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', viewPublic)
                .birthdate == null

        deserializeFromString(jsonMapper, ctx.classLoader.loadClass("example.WithViews"), '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', viewInternal)
                .firstName == 'Bob'
        deserializeFromString(jsonMapper, ctx.classLoader.loadClass("example.WithViews"), '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', viewInternal)
                .birthdate == '08/01/1980'
        deserializeFromString(jsonMapper, ctx.classLoader.loadClass("example.WithViews"), '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', viewInternal)
                .password == null

        deserializeFromString(jsonMapper, ctx.classLoader.loadClass("example.WithViews"), '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', viewAdmin)
                .firstName == 'Bob'
        deserializeFromString(jsonMapper, ctx.classLoader.loadClass("example.WithViews"), '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', viewAdmin)
                .birthdate == '08/01/1980'
        deserializeFromString(jsonMapper, ctx.classLoader.loadClass("example.WithViews"), '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', viewAdmin)
                .password == 'secret'
    }

    @PendingFeature(reason = "Support for @JsonView not yet implemented")
    def 'unwrapped view'() {
        given:
        def ctx = buildContext('example.WithViews', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Outer {
    public String a;
    @JsonView(Runnable.class) @JsonUnwrapped public Nested nested;
}

class Nested {
    public String b;
}
''', true)
        def jsonMapper = ctx.getBean(JsonMapper)

        def outer = ctx.classLoader.loadClass('example.Outer').newInstance()
        outer.a = 'a'
        outer.nested = ctx.classLoader.loadClass('example.Nested').newInstance()
        outer.nested.b = 'b'

        expect:
        serializeToString(jsonMapper, outer) == '{"a":"a"}'
        // abuse Runnable as the view class
        serializeToString(jsonMapper, outer, Runnable) == '{"a":"a","b":"b"}'

        deserializeFromString(jsonMapper, ctx.classLoader.loadClass("example.Outer"), '{"a":"a","b":"b"}').nested.b == null
        deserializeFromString(jsonMapper, ctx.classLoader.loadClass("example.Outer"), '{"a":"a","b":"b"}', Runnable).nested.b == 'b'
    }

    @PendingFeature
    def 'custom serializer'() {
        def ctx = buildContext('example.Test', '''
package example;

import io.micronaut.context.annotation.Bean;import io.micronaut.json.*;
import io.micronaut.json.annotation.*;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.Locale;

@Serdeable
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
        def jsonMapper = ctx.getBean(JsonMapper)

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
        deserializeFromString(jsonMapper, ctx.classLoader.loadClass("example.Test"), '{"foo":"boo","bar":"BAZ"}').foo == 'boo'
        deserializeFromString(jsonMapper, ctx.classLoader.loadClass("example.Test"), '{"foo":"boo","bar":"BAZ"}').bar == 'baz'
        deserializeFromString(jsonMapper, ctx.classLoader.loadClass("example.Test"), '{"foo":"boo","bar":null}').bar == null
    }

    // endregion
}
