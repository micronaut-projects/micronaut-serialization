package io.micronaut.serde.jackson.object

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.exceptions.IntrospectionException
import io.micronaut.core.naming.NameUtils
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.SerdeIntrospections
import jakarta.inject.Provider
import org.intellij.lang.annotations.Language
import spock.lang.PendingFeature

import java.lang.reflect.ParameterizedType
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import java.nio.charset.StandardCharsets

class ObjectSerdeSpec extends AbstractTypeElementSpec {
    private <T> String serializeToString(JsonMapper jsonMapper, T value, Class<?> view = Object.class) {
        return new String(jsonMapper.cloneWithViewClass(view).writeValueAsBytes(value), StandardCharsets.UTF_8)
    }

    static <T> T deserializeFromString(JsonMapper jsonMapper, Class<T> type, @Language("json") String json, Class<?> view = Object.class) {
        return jsonMapper.cloneWithViewClass(view).readValue(json, Argument.of(type))
    }

    protected void setupSerdeRegistry(ApplicationContext context) {
        def classLoader = context.classLoader
        context.registerSingleton(SerdeIntrospections, new SerdeIntrospections() {

            @Override
            def <T> BeanIntrospection<T> getSerializableIntrospection(@NonNull Argument<T> type) {
                try {
                    return classLoader.loadClass(NameUtils.getPackageName(type.type.name) + ".\$" + type.type.simpleName + '$Introspection')
                            .newInstance()
                } catch (ClassNotFoundException e) {
                    throw new IntrospectionException("No introspection")
                }
            }

            @Override
            def <T> BeanIntrospection<T> getDeserializableIntrospection(@NonNull Argument<T> type) {
                try {
                    return classLoader.loadClass(NameUtils.getPackageName(type.type.name) + ".\$" + type.type.simpleName + '$Introspection')
                            .newInstance()
                } catch (ClassNotFoundException e) {
                    throw new IntrospectionException("No introspection for type $type")
                }
            }
        })
    }

    //region JsonSubTypesSpec

    def 'wrapper array'() {
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
        setupSerdeRegistry(compiled)
        def jsonMapper = compiled.getBean(JsonMapper)
        def baseClass = compiled.classLoader.loadClass('example.Base')
        def a = compiled.classLoader.loadClass('example.A').newInstance()
        a.fieldA = 'foo'

        expect:
        deserializeFromString(jsonMapper, baseClass, '["a",{"fieldA":"foo"}]').fieldA == 'foo'
        deserializeFromString(jsonMapper, baseClass, '["b",{"fieldB":"foo"}]').fieldB == 'foo'
        deserializeFromString(jsonMapper, baseClass, '["c",{"fieldB":"foo"}]').fieldB == 'foo'

        serializeToString(jsonMapper, a) == '["a",{"fieldA":"foo"}]'
    }

    def 'wrapper object'() {
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

class A extends Base {
    public String fieldA;
}
class B extends Base {
    public String fieldB;
}
''', true)
        setupSerdeRegistry(compiled)
        def jsonMapper = compiled.getBean(JsonMapper)
        def baseClass = compiled.classLoader.loadClass('example.Base')
        def a = compiled.classLoader.loadClass('example.A').newInstance()
        a.fieldA = 'foo'

        expect:
        deserializeFromString(jsonMapper, baseClass, '{"a":{"fieldA":"foo"}}').fieldA == 'foo'
        deserializeFromString(jsonMapper, baseClass, '{"b":{"fieldB":"foo"}}').fieldB == 'foo'
        deserializeFromString(jsonMapper, baseClass, '{"c":{"fieldB":"foo"}}').fieldB == 'foo'

        serializeToString(jsonMapper, a) == '{"a":{"fieldA":"foo"}}'
    }

    def 'property'() {
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
        setupSerdeRegistry(compiled)
        def jsonMapper = compiled.getBean(JsonMapper)
        def baseClass = compiled.classLoader.loadClass('example.Base')
        def a = compiled.classLoader.loadClass('example.A').newInstance()
        a.fieldA = 'foo'

        expect:
        deserializeFromString(jsonMapper, baseClass, '{"type":"a","fieldA":"foo"}').fieldA == 'foo'
        deserializeFromString(jsonMapper, baseClass, '{"type":"b","fieldB":"foo"}').fieldB == 'foo'
        deserializeFromString(jsonMapper, baseClass, '{"type":"c","fieldB":"foo"}').fieldB == 'foo'

        serializeToString(jsonMapper, a) == '{"type":"a","fieldA":"foo"}'
    }

    def 'deduction'() {
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
        setupSerdeRegistry(compiled)
        def jsonMapper = compiled.getBean(JsonMapper)
        def baseClass = compiled.classLoader.loadClass('example.Base')
        def a = compiled.classLoader.loadClass('example.A').newInstance()
        a.fieldA = 'foo'

        expect:
        deserializeFromString(jsonMapper, baseClass, '{"fieldA":"foo"}').fieldA == 'foo'
        deserializeFromString(jsonMapper, baseClass, '{"fieldB":"foo"}').fieldB == 'foo'

        serializeToString(jsonMapper, a) == '{"fieldA":"foo"}'
    }

    def 'deduction with supertype prop'() {
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
        setupSerdeRegistry(compiled)
        def jsonMapper = compiled.getBean(JsonMapper)
        def baseClass = compiled.classLoader.loadClass('example.Base')
        def a = compiled.classLoader.loadClass('example.A').newInstance()
        a.sup = 'x'
        a.fieldA = 'foo'

        expect:
        deserializeFromString(jsonMapper, baseClass, '{"sup":"x","fieldA":"foo"}').sup == 'x'
        deserializeFromString(jsonMapper, baseClass, '{"sup":"x","fieldA":"foo"}').fieldA == 'foo'
        deserializeFromString(jsonMapper, baseClass, '{"sup":"x","fieldB":"foo"}').sup == 'x'
        deserializeFromString(jsonMapper, baseClass, '{"sup":"x","fieldB":"foo"}').fieldB == 'foo'

        serializeToString(jsonMapper, a) == '{"fieldA":"foo","sup":"x"}'
    }

    def 'deduction unwrapped'() {
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
        setupSerdeRegistry(compiled)
        def jsonMapper = compiled.getBean(JsonMapper)
        def baseClass = compiled.classLoader.loadClass('example.Base1')
        def parsed = deserializeFromString(jsonMapper, baseClass, '{"fieldA1":"foo","sup":"x","fieldA2":"bar"}')

        def a1 = compiled.classLoader.loadClass('example.A1').newInstance()
        a1.fieldA1 = 'foo'
        def a2 = compiled.classLoader.loadClass('example.A2').newInstance()
        a2.sup = 'x'
        a2.fieldA2 = 'bar'
        a1.base2 = a2

        expect:
        parsed.fieldA1 == 'foo'
        parsed.base2.sup == 'x'
        parsed.base2.fieldA2 == 'bar'

        serializeToString(jsonMapper, a1) == '{"fieldA1":"foo","fieldA2":"bar","sup":"x"}'
    }

    void 'unknown property handling on subtypes'() {
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
        setupSerdeRegistry(compiled)
        def jsonMapper = compiled.getBean(JsonMapper)
        def baseClass = compiled.classLoader.loadClass('example.Base')

        expect:
        deserializeFromString(jsonMapper, baseClass, '{"type":".A","foo":"bar"}').class.simpleName == 'A'

        when:
        deserializeFromString(jsonMapper, baseClass, '{"type":".B","foo":"bar"}')
        then:
        thrown DeserializationException
    }

    void 'any setter merge'() {
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
@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS, include = JsonTypeInfo.As.PROPERTY, property = "type")
class Base {
}
class A extends Base {
    private Map<String, String> anySetter = new HashMap<>();
    
    @JsonAnySetter
    void put(String key, String value) {
        anySetter.put(key, value);
    }
}
class B extends Base {
    private Map<String, String> anySetter = new HashMap<>();
    
    @JsonAnySetter
    void put(String key, String value) {
        anySetter.put(key, value);
    }
}
''', true)
        setupSerdeRegistry(compiled)
        def jsonMapper = compiled.getBean(JsonMapper)
        def baseClass = compiled.classLoader.loadClass('example.Base')

        expect:
        deserializeFromString(jsonMapper, baseClass, '{"type":".A","foo":"bar"}').class.simpleName == 'A'
        deserializeFromString(jsonMapper, baseClass, '{"type":".A","foo":"bar"}').anySetter == [foo: 'bar']
        deserializeFromString(jsonMapper, baseClass, '{"type":".B","foo":"bar"}').class.simpleName == 'B'
        deserializeFromString(jsonMapper, baseClass, '{"type":".B","foo":"bar"}').anySetter == [foo: 'bar']

        deserializeFromString(jsonMapper, baseClass, '{"foo":"bar","type":".A"}').anySetter == [foo: 'bar']
        deserializeFromString(jsonMapper, baseClass, '{"foo":"bar","type":".B"}').anySetter == [foo: 'bar']
    }

    def 'JsonTypeName'() {
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
        setupSerdeRegistry(compiled)
        def jsonMapper = compiled.getBean(JsonMapper)
        def baseClass = compiled.classLoader.loadClass('example.Base')
        def a = compiled.classLoader.loadClass('example.A').newInstance()
        a.fieldA = 'foo'

        expect:
        deserializeFromString(jsonMapper, baseClass, '["A",{"fieldA":"foo"}]').fieldA == 'foo'
        deserializeFromString(jsonMapper, baseClass, '["b",{"fieldB":"foo"}]').fieldB == 'foo'

        serializeToString(jsonMapper, a) == '["A",{"fieldA":"foo"}]'
    }

    // endregion

    // region MapperVisitorSpec

    void "nested beans"() {
        given:
        def compiled = buildContext('example.Test', '''
package example;

import io.micronaut.serde.annotation.Serdeable;@io.micronaut.serde.annotation.Serdeable
class A {
    public B b;
    public String bar;
}

@io.micronaut.serde.annotation.Serdeable
class B {
    public String foo;
}
''', true)
        setupSerdeRegistry(compiled)
        def jsonMapper = compiled.getBean(JsonMapper)

        def a = compiled.classLoader.loadClass("example.A").newInstance()
        def b = compiled.classLoader.loadClass("example.B").newInstance()

        a.b = b
        a.bar = "123"
        b.foo = "456"

        expect:
        serializeToString(jsonMapper, b) == '{"foo":"456"}'
        serializeToString(jsonMapper, a) == '{"b":{"foo":"456"},"bar":"123"}'
        deserializeFromString(jsonMapper, compiled.classLoader.loadClass("example.B"), '{"foo":"456"}').foo == "456"
        deserializeFromString(jsonMapper, compiled.classLoader.loadClass("example.A"), '{"b":{"foo":"456"},"bar":"123"}').bar == "123"
        deserializeFromString(jsonMapper, compiled.classLoader.loadClass("example.A"), '{"b":{"foo":"456"},"bar":"123"}').b.foo == "456"
    }

    void "lists"() {
        given:
        def compiled = buildContext('example.Test', '''
package example;

import io.micronaut.serde.annotation.Serdeable;import java.util.List;

@io.micronaut.serde.annotation.Serdeable
class Test {
    public List<String> list;
}
''', true)
        setupSerdeRegistry(compiled)
        def jsonMapper = compiled.getBean(JsonMapper)

        def test = compiled.classLoader.loadClass("example.Test").newInstance()

        test.list = ['foo', 'bar']

        expect:
        serializeToString(jsonMapper, test) == '{"list":["foo","bar"]}'
        deserializeFromString(jsonMapper, compiled.classLoader.loadClass("example.Test"), '{"list":["foo","bar"]}').list == ['foo', 'bar']
    }

    void "maps"() {
        given:
        def compiled = buildContext('example.Test', '''
package example;

import io.micronaut.serde.annotation.Serdeable;
import java.util.Map;

@io.micronaut.serde.annotation.Serdeable
class Test {
    public Map<String, String> map;
}
''', true)
        setupSerdeRegistry(compiled)
        def jsonMapper = compiled.getBean(JsonMapper)

        def test = compiled.classLoader.loadClass("example.Test").newInstance()
        test.map = ['foo': 'bar']

        expect:
        serializeToString(jsonMapper, test) == '{"map":{"foo":"bar"}}'
        deserializeFromString(jsonMapper, compiled.classLoader.loadClass("example.Test"), '{"map":{"foo":"bar"}}').map == ['foo': 'bar']
    }

    void "null map values"() {
        given:
        def compiled = buildContext('example.Test', '''
package example;

import io.micronaut.serde.annotation.Serdeable;
import java.util.Map;

@io.micronaut.serde.annotation.Serdeable
class Test {
    public Map<String, String> map;
}
''', true)
        setupSerdeRegistry(compiled)
        def jsonMapper = compiled.getBean(JsonMapper)

        def test = compiled.classLoader.loadClass("example.Test").newInstance()
        test.map = ['foo': null]

        expect:
        serializeToString(jsonMapper, test) == '{"map":{"foo":null}}'
        deserializeFromString(jsonMapper, compiled.classLoader.loadClass("example.Test"), '{"map":{"foo":null}}').map == ['foo': null]
    }

    void "nested generic"() {
        given:
        def compiled = buildContext('example.Test', '''
package example;

import io.micronaut.serde.annotation.Serdeable;
@io.micronaut.serde.annotation.Serdeable
class A {
    public B<C> b;
}

@io.micronaut.serde.annotation.Serdeable
class B<T> {
    public T foo;
}

@io.micronaut.serde.annotation.Serdeable
class C {
    public String bar;
}
''', true)
        setupSerdeRegistry(compiled)
        def jsonMapper = compiled.getBean(JsonMapper)

        def a = compiled.classLoader.loadClass("example.A").newInstance()
        def b = compiled.classLoader.loadClass("example.B").newInstance()
        def c = compiled.classLoader.loadClass("example.C").newInstance()

        a.b = b
        b.foo = c
        c.bar = "123"

        expect:
        serializeToString(jsonMapper, a) == '{"b":{"foo":{"bar":"123"}}}'
        deserializeFromString(jsonMapper, compiled.classLoader.loadClass("example.A"), '{"b":{"foo":{"bar":"123"}}}').b.foo.bar == "123"
    }

    void "nested generic inline"() {
        given:
        def compiled = buildContext('example.Test', '''
package example;

import io.micronaut.serde.annotation.Serdeable;@io.micronaut.serde.annotation.Serdeable
class A {
    public B<C> b;
}

@io.micronaut.serde.annotation.Serdeable//(inline = true)
class B<T> {
    public T foo;
}

@io.micronaut.serde.annotation.Serdeable
class C {
    public String bar;
}
''', true)
        setupSerdeRegistry(compiled)
        def jsonMapper = compiled.getBean(JsonMapper)

        def a = compiled.classLoader.loadClass("example.A").newInstance()
        def b = compiled.classLoader.loadClass("example.B").newInstance()
        def c = compiled.classLoader.loadClass("example.C").newInstance()

        a.b = b
        b.foo = c
        c.bar = "123"

        expect:
        serializeToString(jsonMapper, a) == '{"b":{"foo":{"bar":"123"}}}'
        deserializeFromString(jsonMapper, compiled.classLoader.loadClass("example.A"), '{"b":{"foo":{"bar":"123"}}}').b.foo.bar == "123"
    }

    void "enum"() {
        given:
        def compiled = buildContext('example.Test', '''
package example;

import io.micronaut.serde.annotation.Serdeable;@io.micronaut.serde.annotation.Serdeable
class A {
    public E e;
}

enum E {
    A, B
}
''', true)
        setupSerdeRegistry(compiled)
        def jsonMapper = compiled.getBean(JsonMapper)

        def a = compiled.classLoader.loadClass("example.A").newInstance()
        a.e = compiled.classLoader.loadClass("example.E").enumConstants[1]

        expect:
        serializeToString(jsonMapper, a) == '{"e":"B"}'
        deserializeFromString(jsonMapper, compiled.classLoader.loadClass("example.A"), '{"e":"A"}').e.name() == 'A'
        deserializeFromString(jsonMapper, compiled.classLoader.loadClass("example.A"), '{"e":"B"}').e.name() == 'B'
    }

    void "nested class"() {
        given:
        def compiled = buildContext('example.Test', '''
package example;

class A {
    @io.micronaut.serde.annotation.Serdeable
    static class B {
    }
}
''', true)
        setupSerdeRegistry(compiled)
        def jsonMapper = compiled.getBean(JsonMapper)

        def b = compiled.classLoader.loadClass('example.A$B').newInstance()

        expect:
        serializeToString(jsonMapper, b) == '{}'
    }

    void "interface"() {
        given:
        def compiled = buildContext('example.Test', '''
package example;

@io.micronaut.serde.annotation.Serdeable//(allowDeserialization = false)
interface Test {
    String getFoo();
}
''', true)
        setupSerdeRegistry(compiled)
        def jsonMapper = compiled.getBean(JsonMapper)
        def testBean = ['getFoo': { Object[] args -> 'bar' }].asType(compiled.classLoader.loadClass('example.Test'))

        expect:
        serializeToString(jsonMapper, testBean) == '{"foo":"bar"}'

        when:
        compiled.classLoader.loadClass('example.$Test$Deserializer')

        then:
        thrown ClassNotFoundException
    }

    void "optional"() {
        given:
        def compiled = buildContext('example.A', '''
package example;

@io.micronaut.serde.annotation.Serdeable
class A {
    public java.util.Optional<B> b;
}

@io.micronaut.serde.annotation.Serdeable
class B {
}
''', true)
        setupSerdeRegistry(compiled)
        def jsonMapper = compiled.getBean(JsonMapper)
        def testBean = compiled.classLoader.loadClass("example.A").newInstance()
        testBean.b = Optional.of(compiled.classLoader.loadClass("example.B").newInstance())

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
        setupSerdeRegistry(ctx)
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
        setupSerdeRegistry(compiled)
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
        setupSerdeRegistry(ctx)
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
        setupSerdeRegistry(ctx)
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
        setupSerdeRegistry(ctx)
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
