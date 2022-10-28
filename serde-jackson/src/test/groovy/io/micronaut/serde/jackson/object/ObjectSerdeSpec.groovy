package io.micronaut.serde.jackson.object

import com.fasterxml.jackson.annotation.JsonAutoDetect
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.exceptions.SerdeException
import io.micronaut.serde.jackson.JsonCompileSpec
import org.intellij.lang.annotations.Language
import spock.lang.PendingFeature

import java.nio.charset.StandardCharsets

class ObjectSerdeSpec extends JsonCompileSpec {
    private <T> String serializeToString(JsonMapper jsonMapper, T value, Class<?> view = Object.class) {
        return new String(jsonMapper.cloneWithViewClass(view).writeValueAsBytes(value), StandardCharsets.UTF_8)
    }

    static <T> T deserializeFromString(JsonMapper jsonMapper, Class<T> type, @Language("json") String json, Class<?> view = Object.class) {
        return jsonMapper.cloneWithViewClass(view).readValue(json, Argument.of(type))
    }

    def "test serialize / deserialize interface impl"() {
        given:
        def context = buildContext('itfeimpl.Test', '''
package itfeimpl;

import com.fasterxml.jackson.annotation.JsonCreator;import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.core.annotation.Introspected;import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    private HttpStatusInfo info = new HttpStatusInfoImpl(200);
    public void setInfo(itfeimpl.HttpStatusInfo info) {
        this.info = info;
    }
    public itfeimpl.HttpStatusInfo getInfo() {
        return info;
    }
}

@Serdeable.Deserializable(as = HttpStatusInfoImpl.class)
interface HttpStatusInfo {
    int code();
}

@Serdeable
class HttpStatusInfoImpl implements HttpStatusInfo {
    private final int code;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    HttpStatusInfoImpl(int code) {
        this.code = code;
    }

    @JsonValue
    @Override public int code() {
       return code;
    }
}
''')
        when:
        def result = jsonMapper.writeValueAsString(typeUnderTest.type.newInstance())

        then:
        result == '{"info":200}'

        when:
        def bean = jsonMapper.readValue(result, typeUnderTest)

        then:
        bean.info.code() == 200

        cleanup:
        context.close()
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

    def 'test JsonSubTypes with wrapper object'() {
        given:
        def compiled = buildContext('example.Base', '''
package example;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Introspected(accessKind = Introspected.AccessKind.FIELD)
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
''')

        def baseClass = compiled.classLoader.loadClass('example.Base')
        def a = newInstance(compiled, 'example.A')
        a.fieldA = 'foo'

        expect:
        deserializeFromString(jsonMapper, baseClass, '{"a":{"fieldA":"foo"}}').fieldA == 'foo'
        deserializeFromString(jsonMapper, baseClass, '{"b":{"fieldB":"foo"}}').fieldB == 'foo'
//        deserializeFromString(jsonMapper, baseClass, '{"c":{"fieldB":"foo"}}').fieldB == 'foo'

        serializeToString(jsonMapper, a) == '{"a":{"fieldA":"foo"}}'

        cleanup:
        compiled.close()
    }

    def 'test JsonSubTypes with property'() {
        given:
        def compiled = buildContext('example.Base', '''
package example;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.core.annotation.Introspected;import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
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
//        deserializeFromString(jsonMapper, baseClass, '{"type":"c","fieldB":"foo"}').fieldB == 'foo'

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
import io.micronaut.core.annotation.Introspected;import io.micronaut.serde.annotation.Serdeable;

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

    def 'test @JsonSubTypes with @JsonTypeName'() {
        given:
        def compiled = buildContext('example.Base', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
@JsonSubTypes({
    @JsonSubTypes.Type(A.class),
    @JsonSubTypes.Type(B.class)
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
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
        deserializeFromString(jsonMapper, baseClass, '{"A": {"fieldA":"foo"}}').fieldA == 'foo'
        deserializeFromString(jsonMapper, baseClass, '{"b":{"fieldB":"foo"}}').fieldB == 'foo'

        serializeToString(jsonMapper, a) == '{"A":{"fieldA":"foo"}}'

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
        def context = buildContext('example.Test', '''
package example;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
interface Test {
    String getFoo();
}
''')
        def testBean = ['getFoo': { Object[] args -> 'bar' }].asType(context.classLoader.loadClass('example.Test'))

        expect:
        writeJson(jsonMapper, testBean) == '{"foo":"bar"}'

        cleanup:
        context.close()
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

    void "injected serializer uses Serializer.isEmpty"() {
        given:
        def compiled = buildContext('example.A', '''
package example;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.Serializer;
import jakarta.inject.Singleton;
import io.micronaut.core.type.Argument;
import java.io.IOException;

@Serdeable//(allowDeserialization = false)
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class A {
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public B b;
}

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class B {
    public boolean present;
}

@Singleton
class BSerializer implements Serializer<B> {
    @Override public void serialize(Encoder encoder,
                      EncoderContext context,
                      Argument<? extends example.B> type,
                      example.B value)throws IOException {
        encoder.encodeBoolean(value.present);
    }

    @Override
    public boolean isEmpty(EncoderContext context, B value) {
        return value == null || !value.present;
    }
}
''', true)
        def jsonMapper = compiled.getBean(JsonMapper)

        def bPresent = newInstance(compiled, 'example.B')
        bPresent.present = true
        def bAbsent = newInstance(compiled, 'example.B')
        bAbsent.present = false

        def aPresent = newInstance(compiled, 'example.A')
        aPresent.b = bPresent
        def aAbsent = newInstance(compiled, 'example.A')
        aAbsent.b = bAbsent

        expect:
        serializeToString(jsonMapper, aPresent) == '{"b":true}'
        serializeToString(jsonMapper, aAbsent) == '{}'
    }

    def 'simple views'() {
        given:
        def ctx = buildContext('''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonView(Public.class)
@Introspected(accessKind = Introspected.AccessKind.FIELD)
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
''')

        def withViews = newInstance(ctx, 'example.WithViews')
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

        cleanup:
        ctx.close()
    }

    def 'unwrapped view'() {
        given:
        def ctx = buildContext('example.Outer', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Outer {
    public String a;
    @JsonView(Runnable.class) @JsonUnwrapped public Nested nested;
}

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Nested {
    public String b;
}
''')

        def outer = newInstance(ctx, 'example.Outer')
        outer.a = 'a'
        outer.nested = newInstance(ctx, 'example.Nested')
        outer.nested.b = 'b'

        expect:
        serializeToString(jsonMapper, outer) == '{"a":"a"}'
        // abuse Runnable as the view class
        serializeToString(jsonMapper, outer, Runnable) == '{"a":"a","b":"b"}'

        jsonMapper.readValue('{"a":"a","b":"b"}', typeUnderTest).nested?.b == null
        deserializeFromString(jsonMapper, ctx.classLoader.loadClass("example.Outer"), '{"a":"a","b":"b"}', Runnable).nested.b == 'b'
    }

//    @PendingFeature
//    def 'custom serializer'() {
//        def ctx = buildContext('example.Test', '''
//package example;
//
//import io.micronaut.context.annotation.Bean;
//import io.micronaut.json.*;
//import io.micronaut.json.annotation.*;
//import jakarta.inject.Singleton;
//import java.io.IOException;
//import java.util.Locale;
//
//@Serdeable
//class Test {
//    public String foo;
//    @CustomSerializer(serializer = UpperCaseSer.class, deserializer = LowerCaseDeser.class)
//    public String bar;
//}
//
//@Singleton
//@Bean(typed = UpperCaseSer.class)
//class UpperCaseSer implements Serializer<String> {
//    @Override
//    public void serialize(Encoder encoder, String value) throws IOException {
//        encoder.encodeString(value.toUpperCase(Locale.ROOT));
//    }
//
//    @Override
//    public boolean isEmpty(EncoderContext context, String value) {
//        return value.isEmpty();
//    }
//}
//
//@Singleton
//@Bean(typed = LowerCaseDeser.class)
//class LowerCaseDeser implements Deserializer<String> {
//    @Override
//    public String deserialize(Decoder decoder) throws IOException {
//        return decoder.decodeString().toLowerCase(Locale.ROOT);
//    }
//}
//''', true)
//        def jsonMapper = ctx.getBean(JsonMapper)
//
//        def testInstance = ctx.classLoader.loadClass('example.Test').newInstance()
//
//        when:
//        testInstance.foo = 'boo'
//        testInstance.bar = 'Baz'
//        then: 'normal ser'
//        serializeToString(serializer, testInstance) == '{"foo":"boo","bar":"BAZ"}'
//
//        when:
//        testInstance.foo = 'boo'
//        testInstance.bar = ''
//        then: 'empty ser is skipped'
//        serializeToString(serializer, testInstance) == '{"foo":"boo"}'
//
//        when:
//        testInstance.foo = 'boo'
//        testInstance.bar = null
//        then: 'null ser is skipped'
//        serializeToString(serializer, testInstance) == '{"foo":"boo"}'
//
//        expect: 'deser'
//        deserializeFromString(jsonMapper, ctx.classLoader.loadClass("example.Test"), '{"foo":"boo","bar":"BAZ"}').foo == 'boo'
//        deserializeFromString(jsonMapper, ctx.classLoader.loadClass("example.Test"), '{"foo":"boo","bar":"BAZ"}').bar == 'baz'
//        deserializeFromString(jsonMapper, ctx.classLoader.loadClass("example.Test"), '{"foo":"boo","bar":null}').bar == null
//    }

    void "simple bean"() {
        given:
        def context = buildContext('''
package example;

import io.micronaut.core.annotation.Introspected;

@io.micronaut.serde.annotation.Serdeable
@Introspected(accessKind = {Introspected.AccessKind.METHOD, Introspected.AccessKind.FIELD})
class Test {
    public String a;
    private String b;

    Test() {}

    public String getB() {
        return b;
    }

    public void setB(String b) {
        this.b = b;
    }
}
''')

        def typeUnderTest = argumentOf(context, 'example.Test')
        def deserialized = deserializeFromString(jsonMapper, typeUnderTest.type, '{"a": "foo", "b": "bar"}')
        def testBean = newInstance(context, 'example.Test')
        testBean.a = "foo"
        testBean.b = "bar"
        def serialized = serializeToString(jsonMapper, testBean)

        expect:
        deserialized.a == "foo"
        deserialized.b == "bar"
        serialized == '{"b":"bar","a":"foo"}'

        cleanup:
        context.close()
    }

    void "JsonProperty on field"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.JsonProperty;import io.micronaut.core.annotation.Introspected;
@io.micronaut.serde.annotation.Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    @JsonProperty("foo")
    public String bar;
}
''', [:])
        def deserialized = jsonMapper.readValue('{"foo": "42"}', typeUnderTest)
        beanUnderTest.bar = "42"
        def serialized = writeJson(jsonMapper, beanUnderTest)

        expect:
        deserialized.bar == "42"
        serialized == '{"foo":"42"}'

        cleanup:
        context.close()
    }

    void "JsonProperty on getter"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.JsonProperty;
@io.micronaut.serde.annotation.Serdeable
class Test {
    private String bar;

    @JsonProperty("foo")
    public String getBar() {
        return bar;
    }

    public void setBar(String bar) {
        this.bar = bar;
    }
}
''', [:])
        def deserialized = jsonMapper.readValue('{"foo": "42"}', typeUnderTest)
        beanUnderTest.bar = "42"
        def serialized = writeJson(jsonMapper, beanUnderTest)

        expect:
        deserialized.bar == "42"
        serialized == '{"foo":"42"}'

        cleanup:
        context.close()
    }

    void "JsonProperty on is-getter"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    private boolean bar;

    @JsonProperty("foo")
    public boolean isBar() {
        return bar;
    }

    public void setBar(boolean bar) {
        this.bar = bar;
    }
}
''', [:])
        def deserialized = jsonMapper.readValue('{"foo": true}', typeUnderTest)
        beanUnderTest.bar = true
        def serialized = writeJson(jsonMapper, beanUnderTest)

        expect:
        deserialized.bar == true
        serialized == '{"foo":true}'

        cleanup:
        context.close()
    }

    void "JsonProperty on accessors without prefix"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    private String bar;

    @JsonProperty
    public String bar() {
        return bar;
    }

    @JsonProperty
    public void bar(String bar) {
        this.bar = bar;
    }
}
''', [:])
        def deserialized = jsonMapper.readValue('{"bar": "42"}', typeUnderTest)
        beanUnderTest.bar = "42"
        def serialized = serializeToString(jsonMapper, beanUnderTest)

        expect:
        deserialized.bar == "42"
        serialized == '{"bar":"42"}'

        cleanup:
        context.close()
    }

    void "JsonCreator constructor"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    @JsonProperty("foo")
    private final String bar;

    @JsonCreator
    public Test(@JsonProperty("foo") String bar) {
        this.bar = bar;
    }

    public String getBar() {
        return bar;
    }
}
''')
        def deserialized = jsonMapper.readValue('{"foo": "42"}', typeUnderTest)
        def testBean = newInstance(context, 'example.Test', "42")
        def serialized = writeJson(jsonMapper, testBean)

        expect:
        deserialized.bar == "42"
        serialized == '{"foo":"42"}'

        cleanup:
        context.close()
    }

    void "JsonCreator with parameter names"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
@io.micronaut.serde.annotation.Serdeable
class Test {
    public final String foo;
    public final String bar;

    @JsonCreator
    public Test(String foo, String bar) {
        this.foo = foo;
        this.bar = bar;
    }
}
''')
        def deserialized = jsonMapper.readValue('{"foo": "42", "bar": "56"}', typeUnderTest)

        expect:
        deserialized.foo == "42"
        deserialized.bar == "56"

        cleanup:
        context.close()
    }

    void "implicit creator with parameter names"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
@io.micronaut.serde.annotation.Serdeable
class Test {
    public final String foo;
    public final String bar;

    public Test(String foo, String bar) {
        this.foo = foo;
        this.bar = bar;
    }
}
''')
        def deserialized = jsonMapper.readValue('{"foo": "42", "bar": "56"}', typeUnderTest)

        expect:
        deserialized.foo == "42"
        deserialized.bar == "56"

        cleanup:
        context.close()
    }

    void "JsonCreator with single parameter of same name"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
@io.micronaut.serde.annotation.Serdeable
class Test {
    public final String foo;

    @JsonCreator
    public Test(String foo) {
        this.foo = foo;
    }
}
''')
        def deserialized = jsonMapper.readValue('{"foo": "42"}', typeUnderTest)

        expect:
        deserialized.foo == "42"

        cleanup:
        context.close()
    }

    @PendingFeature(reason = 'single-parameter json creator. Dont think we should support this, can be done with delegating mode for JsonCreator')
    void "JsonCreator with single parameter of different name"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
@io.micronaut.serde.annotation.Serdeable
class Test {
    public final String foo;

    @JsonCreator
    public Test(String bar) {
        this.foo = bar;
    }
}
''')
        def deserialized = jsonMapper.readValue('"42"', typeUnderTest)

        expect:
        deserialized.foo == "42"

        cleanup:
        context.close()
    }

    void "JsonCreator constructor with properties mode set"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    @JsonProperty("foo")
    private final String bar;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public Test(@JsonProperty("foo") String bar) {
        this.bar = bar;
    }

    public String getBar() {
        return bar;
    }
}
''')
        def deserialized = jsonMapper.readValue('{"foo": "42"}', typeUnderTest)
        def testBean = newInstance(context, 'example.Test', "42")
        def serialized = writeJson(jsonMapper, testBean)

        expect:
        deserialized.bar == "42"
        serialized == '{"foo":"42"}'

        cleanup:
        context.close()
    }

    void "JsonCreator static method"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    @JsonProperty("foo")
    private final String bar;

    private Test(String bar) {
        this.bar = bar;
    }

    @JsonCreator
    public static Test create(@JsonProperty("foo") String bar) {
        return new Test(bar);
    }

    public String getBar() {
        return bar;
    }
}
''')
        def deserialized = jsonMapper.readValue('{"foo": "42"}', typeUnderTest)
        def testBean = newInstance(context, 'example.Test', "42")
        def serialized = writeJson(jsonMapper, testBean)

        expect:
        deserialized.bar == "42"
        serialized == '{"foo":"42"}'

        cleanup:
        context.close()
    }

    void "JsonCreator no getter"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    private final String bar;

    @JsonCreator
    public Test(@JsonProperty("foo") String bar) {
        this.bar = bar;
    }
}
''')
        def deserialized = jsonMapper.readValue('{"foo": "42"}', typeUnderTest)
        def testBean = newInstance(context, 'example.Test', "42")
        def serialized = writeJson(jsonMapper, testBean)

        expect:
        deserialized.bar == "42"
        serialized == '{}'

        cleanup:
        context.close()
    }

    @PendingFeature(reason = 'duplicate property errors')
    @SuppressWarnings('JsonDuplicatePropertyKeys')
    void "duplicate property throws exception"() {
        given:
        def context = buildContext('example.Test', '''
package example;

@io.micronaut.serde.annotation.Serdeable
class Test {
    public String foo;
}
''')

        when:
        jsonMapper.readValue('{"foo": "42", "foo": "43"}', typeUnderTest)

        then:
        thrown SerdeException

        cleanup:
        context.close()
    }

    @SuppressWarnings('JsonDuplicatePropertyKeys')
    void "missing required property throws exception"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    String foo;

    @JsonCreator
    Test(@JsonProperty(value = "foo", required = true) String foo) {
        this.foo = foo;
    }
}
''')

        when:
        jsonMapper.readValue('{}', typeUnderTest)

        then:
        thrown SerdeException

        cleanup:
        context.close()
    }

    void "missing required property throws exception, many variables"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    String v0, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15,
    v16, v17, v18, v19, v20, v21, v22, v23, v24, v25, v26, v27, v28, v29, v30, v31,
    v32, v33, v34, v35, v36, v37, v38, v39, v40, v41, v42, v43, v44, v45, v46, v47,
    v48, v49, v50, v51, v52, v53, v54, v55, v56, v57, v58, v59, v60, v61, v62, v63,
    v64, v65, v66, v67, v68, v69, v70, v71, v72, v73, v74, v75, v76, v77, v78, v79;

    @JsonCreator
    public Test(
            @JsonProperty(value = "v7", required = true) String v7,
            @JsonProperty(value = "v14", required = true) String v14,
            @JsonProperty(value = "v75", required = true) String v75
    ) {
        this.v7 = v7;
        this.v14 = v14;
        this.v75 = v75;
    }
}
''')

        when:
        jsonMapper.readValue('{"v7": "42", "v75": "43"}', typeUnderTest)

        then:
        def e = thrown SerdeException
        // with the right message please
        e.message.contains("v14")

        cleanup:
        context.close()
    }

    void "unknown properties lead to error"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
@JsonIgnoreProperties(ignoreUnknown = false)
@io.micronaut.serde.annotation.Serdeable
class Test {
    String foo;
}
''')

        when:
        jsonMapper.readValue('{"foo": "1", "bar": "2"}', typeUnderTest)

        then:
        thrown SerdeException

        cleanup:
        context.close()
    }

    void "unknown properties with proper annotation"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@JsonIgnoreProperties(ignoreUnknown = true)
@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    public String foo;
}
''')

        def des = jsonMapper.readValue('{"foo": "1", "bar": "2"}', typeUnderTest)

        expect:
        des.foo == "1"

        cleanup:
        context.close()
    }

    void "unknown properties with proper annotation, complex"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;

@JsonIgnoreProperties(ignoreUnknown = true)
@io.micronaut.serde.annotation.Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    public String foo;
}
''')

        def des = jsonMapper.readValue('{"foo": "1", "bar": [1, 2]}', typeUnderTest)

        expect:
        des.foo == "1"

        cleanup:
        context.close()
    }

    void "json ignore"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@JsonIgnoreProperties(ignoreUnknown = true)
@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    @JsonIgnore public String foo;
    public String bar;
}
''', [:])

        def des = jsonMapper.readValue('{"foo": "1", "bar": "2"}', typeUnderTest)
        beanUnderTest.foo = "1"
        beanUnderTest.bar = "2"
        def serialized = writeJson(jsonMapper, beanUnderTest)

        expect:
        des.foo == null
        des.bar == "2"
        serialized == '{"bar":"2"}'

        cleanup:
        context.close()
    }

    void "nullable"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
@JsonInclude(JsonInclude.Include.NON_ABSENT)
class Test {
    @Nullable String foo;
}
''', [:])

        def des = jsonMapper.readValue('{"foo": null}', typeUnderTest)
        beanUnderTest.foo = null

        expect:
        des.foo == null
        writeJson(jsonMapper, beanUnderTest) == '{}'

        cleanup:
        context.close()
    }

    void "nullable setter"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    private String foo;

    public void setFoo(@Nullable String foo) {
        this.foo = foo;
    }
}
''')

        expect:
        jsonMapper.readValue('{"foo": null}', typeUnderTest).foo == null

        cleanup:
        context.close()
    }

    void "unwrapped"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    @JsonUnwrapped public Name name = new Name();
}

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Name {
    public String first;
    public String last;
}
''', [:])

        def des = jsonMapper.readValue('{"first":"foo","last":"bar"}', typeUnderTest)
        beanUnderTest.name.first = "foo"
        beanUnderTest.name.last = "bar"

        expect:
        writeJson(jsonMapper, beanUnderTest) == '{"first":"foo","last":"bar"}'
        des.name != null
        des.name.first == "foo"
        des.name.last == "bar"

        cleanup:
        context.close()
    }

    void "aliases"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    @JsonAlias("bar")
    public String foo;
}
''')

        expect:
        jsonMapper.readValue('{"foo": "42"}', typeUnderTest).foo == '42'
        jsonMapper.readValue('{"bar": "42"}', typeUnderTest).foo == '42'

        cleanup:
        context.close()
    }

    void "value and creator"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    @JsonValue
    public final String foo;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public Test(String foo) {
        this.foo = foo;
    }
}
''')
        def testBean = newInstance(context, 'example.Test', 'bar')

        expect:
        deserializeFromString(jsonMapper, testBean.getClass(), '"bar"').foo == 'bar'
        serializeToString(jsonMapper, testBean) == '"bar"'
    }

    void "creator with optional parameter"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;

@io.micronaut.serde.annotation.Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    public final String foo;
    public final String bar;

    @JsonCreator
    public Test(@Nullable @JsonProperty("foo") String foo, @JsonProperty(value = "bar", required = true) String bar) {
        this.foo = foo;
        this.bar = bar;
    }
}
''')
        typeUnderTest = argumentOf(context, 'example.Test')

        expect:
        deserializeFromString(jsonMapper, typeUnderTest.type, '{"foo":"123","bar":"456"}').foo == '123'
        deserializeFromString(jsonMapper, typeUnderTest.type, '{"foo":"123","bar":"456"}').bar == '456'

        deserializeFromString(jsonMapper, typeUnderTest.type, '{"bar":"456"}').foo == null
        deserializeFromString(jsonMapper, typeUnderTest.type, '{"bar":"456"}').bar == '456'

        when:
        deserializeFromString(jsonMapper, typeUnderTest.type, '{"foo":"123"}')
        then:
        thrown SerdeException

        cleanup:
        context.close()
    }

    void "@JsonValue on toString"() {
        given:
        def context = buildContext('''
package example;

import com.fasterxml.jackson.annotation.*;
@io.micronaut.serde.annotation.Serdeable
class Test {
    public final String foo;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public Test(String foo) {
        this.foo = foo;
    }

    @Override
    @JsonValue
    public String toString() {
        return foo;
    }
}
''')
        def testBean = newInstance(context, 'example.Test', 'bar')

        expect:
        serializeToString(jsonMapper, testBean) == '"bar"'

        cleanup:
        context.close()
    }

    void "optional"() {
        given:
        def compiled = buildContext('example.Test', '''
package example;

import io.micronaut.core.annotation.Introspected;
import java.util.Optional;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    public Optional<String> foo = Optional.empty();
}
''', [:])
        beanUnderTest.foo = Optional.of('bar')

        expect:
        jsonMapper.readValue('{"foo":"bar"}', typeUnderTest).foo.get() == 'bar'
        !jsonMapper.readValue('{"foo":null}', typeUnderTest).foo.isPresent()
        !jsonMapper.readValue('{}', typeUnderTest).foo.isPresent()
        writeJson(jsonMapper, beanUnderTest) == '{"foo":"bar"}'
    }

    // @PendingFeature(reason = 'Regression in Micronaut 4.0 when reading property values')
    void "optional nullable mix"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.JsonSetter;
import io.micronaut.core.annotation.Nullable;
import java.util.Optional;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    @Nullable
    private String foo;

    public Optional<String> getFoo() {
        return Optional.ofNullable(foo);
    }

    @JsonSetter("foo")
    public void setFoo(@Nullable String foo) {
        this.foo = foo;
    }
}
''', [:])
        beanUnderTest.foo = 'bar'

        expect:
        jsonMapper.readValue('{"foo":"bar"}', typeUnderTest).foo.get() == 'bar'
        writeJson(jsonMapper, beanUnderTest) == '{"foo":"bar"}'

        cleanup:
        context.close()
    }

    void "@JsonInclude"() {
        given:
        def context = buildContext('''
package example;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Introspected;
import java.util.*;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public String alwaysString;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String nonNullString;
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public String nonAbsentString;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String nonEmptyString;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String[] nonEmptyArray;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<String> nonEmptyList;

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public Optional<String> nonAbsentOptionalString;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Optional<List<String>> nonEmptyOptionalList;
}
''')
        def with = newInstance(context, 'example.Test')
        with.alwaysString = 'a';
        with.nonNullString = 'a';
        with.nonAbsentString = 'a';
        with.nonEmptyString = 'a';
        with.nonEmptyArray = ['a'];
        with.nonEmptyList = ['a'];
        with.nonAbsentOptionalString = Optional.of('a');
        with.nonEmptyOptionalList = Optional.of(['a']);

        def without = newInstance(context, 'example.Test')
        without.alwaysString = null
        without.nonNullString = null
        without.nonAbsentString = null
        without.nonEmptyString = null
        without.nonEmptyArray = []
        without.nonEmptyList = []
        without.nonAbsentOptionalString = Optional.empty()
        without.nonEmptyOptionalList = Optional.of([])

        expect:
        writeJson(jsonMapper, with) == '{"alwaysString":"a","nonNullString":"a","nonAbsentString":"a","nonEmptyString":"a","nonEmptyArray":["a"],"nonEmptyList":["a"],"nonAbsentOptionalString":"a","nonEmptyOptionalList":["a"]}'
        writeJson(jsonMapper, without) == '{"alwaysString":null}'
    }

    void "missing properties are not overwritten"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import io.micronaut.core.annotation.Introspected;import io.micronaut.core.annotation.Nullable;
import java.util.Optional;
@io.micronaut.serde.annotation.Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    @Nullable
    public String foo = "bar";
}
''')

        expect:
        jsonMapper.readValue('{}', typeUnderTest).foo == 'bar'
        jsonMapper.readValue('{"foo":null}', typeUnderTest).foo == null

        cleanup:
        context.close()
    }

    void "@JsonAnyGetter"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import java.util.*;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    @JsonAnyGetter
    Map<String, String> anyGetter() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("foo", "bar");
        map.put("123", "456");
        return map;
    }
}
''', [:])

        expect:
        writeJson(jsonMapper, beanUnderTest) == '{"foo":"bar","123":"456"}'
    }

    void "@JsonAnySetter"() {
        given:
        def context = buildContext('example.Test', '''
package example;

import java.util.*;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    private Map<String, String> anySetter = new HashMap<>();

    @JsonAnySetter
    void put(String key, String value) {
        anySetter.put(key, value);
    }
}
''')

        expect:
        jsonMapper.readValue('{"foo":"bar","123":"456"}', typeUnderTest).anySetter == ['foo': 'bar', '123': '456']

        cleanup:
        context.close()
    }

    void 'unwrapped ignore unknown outer'() {
        given:
        def context = buildContext('example.A', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonIgnoreProperties(ignoreUnknown = true)
class A {
    @JsonUnwrapped B b;
}
@Serdeable
@JsonIgnoreProperties(ignoreUnknown = false)
class B {
}
''')

        expect:
        jsonMapper.readValue('{"foo":"bar"}', typeUnderTest) != null

        cleanup:
        context.close()
    }

    void 'unwrapped ignore unknown inner'() {
        given:
        def context = buildContext('example.A', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonIgnoreProperties(ignoreUnknown = false)
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class A {
    @JsonUnwrapped B b;
}

@Serdeable
@JsonIgnoreProperties(ignoreUnknown = true)
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class B {
}
''')

        when:
        jsonMapper.readValue('{"foo":"bar"}', typeUnderTest) != null

        then:
        def e = thrown SerdeException
        expect: 'error should have the name of the outer class'
        e.message.contains("A")

        cleanup:
        context.close()
    }

    void 'unwrapped ignore unknown neither'() {
        given:
        def context = buildContext('example.Outer', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonIgnoreProperties(ignoreUnknown = false)
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Outer {
    @JsonUnwrapped B b;
}

@Serdeable
@JsonIgnoreProperties(ignoreUnknown = false)
class B {
}
''')

        when:
        jsonMapper.readValue('{"foo":"bar"}', typeUnderTest)

        then:
        def e = thrown SerdeException
        expect: 'error should have the name of the outer class'
        e.message.contains("Outer")

        cleanup:
        context.close()
    }

    void 'generic complex collection member supertype serialize'() {
        given:
        def compiled = buildContext('example.Sub', '''
package example;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Typo {
    public String name;
}

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Sub extends Sup<Typo> {
}

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Sup<T> {
    public List<T> value;
}
''')

        def baseClass = compiled.classLoader.loadClass('example.Sub')
        def a = newInstance(compiled, 'example.Sub')
        def typo = newInstance(compiled, 'example.Typo')
        typo.name = "Bob"
        a.value = Arrays.asList(typo);

        expect:
        serializeToString(jsonMapper, a, baseClass) == '{"value":[{"name":"Bob"}]}'
    }

    void 'generic complex collection member supertype deserialize'() {
        given:
        def compiled = buildContext('example.Sub', '''
package example;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Typo {
    public String name;
}

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Sub extends Sup<Typo> {
}

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Sup<T> {
    public List<T> value;
}
''')

        expect:
        def baseClass = compiled.classLoader.loadClass('example.Sub')
        deserializeFromString(jsonMapper, baseClass, '{"value":[{"name":"Bob"}]}').value.get(0).name == 'Bob'
    }


    void 'generic collection member supertype'() {
        given:
        def compiled = buildContext('example.Sub', '''
package example;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
class Sub extends Sup<String> {
}

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Sup<T> {
    public List<T> value;
}
''')

        expect:
        jsonMapper.readValue('{"value":["bar"]}', typeUnderTest).value == ['bar']

        cleanup:
        compiled.close()
    }


    void 'generic supertype'() {
        given:
        def compiled = buildContext('example.Sub', '''
package example;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Sub extends Sup<String> {
}

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Sup<T> {
    public T value;
}
''')

        expect:
        jsonMapper.readValue('{"value":"bar"}', typeUnderTest).value == 'bar'
    }

    void 'generic supertype mixed'() {
        given:
        def context = buildContext('example.Subsub', '''
package example;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Subsub extends Sub<java.util.List<String>> {
}
@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Sub<T> extends Sup<String> {
    public java.util.List<T> value2;
}
@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Sup<T> {
    public T value;
}
''')

        expect:
        jsonMapper.readValue('{"value":"bar","value2":[["foo","bar"]]}', typeUnderTest).value == 'bar'
        jsonMapper.readValue('{"value":"bar","value2":[["foo","bar"]]}', typeUnderTest).value2 == [["foo", "bar"]]
    }

    @PendingFeature(reason = '@JsonAutoDetect')
    void 'auto-detect visibility homogenous'() {
        given:
        def context = buildContext('example.Test', """
package example;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

@JsonAutoDetect(
    getterVisibility = JsonAutoDetect.Visibility.$configuredVisibility,
    isGetterVisibility = JsonAutoDetect.Visibility.$configuredVisibility,
    setterVisibility = JsonAutoDetect.Visibility.$configuredVisibility,
    fieldVisibility = JsonAutoDetect.Visibility.$configuredVisibility
)
@io.micronaut.serde.annotation.Serdeable
class Test {
    $declaredVisibility String field = "unchanged";

    private String setterValue = "unchanged";

    $declaredVisibility void setSetter(String value) {
        this.setterValue = value;
    }

    private String getterValue = "unchanged";

    $declaredVisibility String getGetter() {
        return getterValue;
    }

    private String isGetterValue = "unchanged";

    $declaredVisibility String isIsGetter() {
        return isGetterValue;
    }
}
""", [:])
        def instance = beanUnderTest
        instance.field = 'foo'
        instance.setterValue = 'qux'
        instance.getterValue = 'bar'
        instance.isGetterValue = 'baz'

        // json with all fields
        def fullJson = '{"field":"foo","getter":"bar","isGetter":"baz","setter":"qux"}'

        // json with only the serializable fields
        def expectedJson = appears ? '{"field":"foo","getter":"bar","isGetter":"baz"}' : '{}'

        expect:
        jsonMapper.readValue(fullJson, typeUnderTest).field == appears ? 'foo' : 'unchanged'
        jsonMapper.readValue(fullJson, typeUnderTest).setterValue == appears ? 'qux' : 'unchanged'
        jsonMapper.readValue(fullJson, typeUnderTest).getterValue == 'unchanged' // never written
        jsonMapper.readValue(fullJson, typeUnderTest).isGetterValue == 'unchanged' // never written

        writeJson(jsonMapper, instance) == expectedJson

        where:
        configuredVisibility                           | declaredVisibility | appears
        // hide private by default
        JsonAutoDetect.Visibility.DEFAULT              | 'private'          | false
        // hide package-private by default
        JsonAutoDetect.Visibility.DEFAULT              | ''                 | false
        // various access modes
        // ANY is not supported (we can't access private fields)
        JsonAutoDetect.Visibility.NON_PRIVATE          | 'private'          | false
        JsonAutoDetect.Visibility.NON_PRIVATE          | ''                 | true
        JsonAutoDetect.Visibility.NON_PRIVATE          | 'protected'        | true
        JsonAutoDetect.Visibility.NON_PRIVATE          | 'public'           | true
        JsonAutoDetect.Visibility.PROTECTED_AND_PUBLIC | 'private'          | false
        JsonAutoDetect.Visibility.PROTECTED_AND_PUBLIC | ''                 | false
        JsonAutoDetect.Visibility.PROTECTED_AND_PUBLIC | 'protected'        | true
        JsonAutoDetect.Visibility.PROTECTED_AND_PUBLIC | 'public'           | true
        JsonAutoDetect.Visibility.PUBLIC_ONLY          | 'private'          | false
        JsonAutoDetect.Visibility.PUBLIC_ONLY          | ''                 | false
        JsonAutoDetect.Visibility.PUBLIC_ONLY          | 'protected'        | false
        JsonAutoDetect.Visibility.PUBLIC_ONLY          | 'public'           | true
        JsonAutoDetect.Visibility.NONE                 | 'private'          | false
        JsonAutoDetect.Visibility.NONE                 | ''                 | false
        JsonAutoDetect.Visibility.NONE                 | 'protected'        | false
        JsonAutoDetect.Visibility.NONE                 | 'public'           | false
    }

    @PendingFeature(reason = '@JsonAutoDetect')
    void 'auto-detect visibility heterogenous'() {
        given:
        def context = buildContext('example.Test', """
package example;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

@JsonAutoDetect(
    getterVisibility = JsonAutoDetect.Visibility.$configuredGetterVisibility,
    isGetterVisibility = JsonAutoDetect.Visibility.$configuredIsGetterVisibility,
    setterVisibility = JsonAutoDetect.Visibility.$configuredSetterVisibility,
    fieldVisibility = JsonAutoDetect.Visibility.$configuredFieldVisibility
)
@io.micronaut.serde.annotation.Serdeable
class Test {
    $declaredFieldVisibility String field = "unchanged";

    private String setterValue = "unchanged";

    $declaredSetterVisibility void setSetter(String value) {
        this.setterValue = value;
    }

    private String getterValue = "unchanged";

    $declaredGetterVisibility String getGetter() {
        return getterValue;
    }

    private String isGetterValue = "unchanged";

    $declaredIsGetterVisibility String isIsGetter() {
        return isGetterValue;
    }
}
""", [:])
        def instance = beanUnderTest
        instance.field = 'foo'
        instance.setterValue = 'qux'
        instance.getterValue = 'bar'
        instance.isGetterValue = 'baz'

        // json with all fields
        def fullJson = '{"field":"foo","getter":"bar","isGetter":"baz","setter":"qux"}'

        // json with only the visible fields
        def expectedJson = '{'
        if (fieldAppears) expectedJson += '"field":"foo",'
        if (getterAppears) expectedJson += '"getter":"bar",'
        if (isGetterAppears) expectedJson += '"isGetter":"baz",'
        if (expectedJson.length() > 1) expectedJson = expectedJson.substring(0, expectedJson.length() - 1)
        expectedJson += '}'

        expect:
        jsonMapper.readValue(fullJson, typeUnderTest).field == fieldAppears ? 'foo' : 'unchanged'
        jsonMapper.readValue(fullJson, typeUnderTest).setterValue == setterAppears ? 'qux' : 'unchanged'
        jsonMapper.readValue(fullJson, typeUnderTest).getterValue == 'unchanged' // never written
        jsonMapper.readValue(fullJson, typeUnderTest).isGetterValue == 'unchanged' // never written

        writeJson(jsonMapper, instance) == expectedJson

        where:

        configuredFieldVisibility             | declaredFieldVisibility | fieldAppears
        JsonAutoDetect.Visibility.NON_PRIVATE | ''                      | true
        JsonAutoDetect.Visibility.DEFAULT     | ''                      | false
        JsonAutoDetect.Visibility.DEFAULT     | ''                      | false
        JsonAutoDetect.Visibility.DEFAULT     | ''                      | false
        __
        configuredGetterVisibility | declaredGetterVisibility | getterAppears
        JsonAutoDetect.Visibility.DEFAULT | '' | false
        JsonAutoDetect.Visibility.NON_PRIVATE | '' | true
        JsonAutoDetect.Visibility.DEFAULT | '' | false
        JsonAutoDetect.Visibility.DEFAULT | '' | false
        __
        configuredIsGetterVisibility | declaredIsGetterVisibility | isGetterAppears
        JsonAutoDetect.Visibility.DEFAULT | '' | false
        JsonAutoDetect.Visibility.DEFAULT | '' | false
        JsonAutoDetect.Visibility.NON_PRIVATE | '' | true
        JsonAutoDetect.Visibility.DEFAULT | '' | false
        __
        configuredSetterVisibility | declaredSetterVisibility | setterAppears
        JsonAutoDetect.Visibility.DEFAULT | '' | false
        JsonAutoDetect.Visibility.DEFAULT | '' | false
        JsonAutoDetect.Visibility.DEFAULT | '' | false
        JsonAutoDetect.Visibility.NON_PRIVATE | '' | true
    }

    void 'JsonIgnoreType'() {
        given:
        def compiled = buildContext('''
package example;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import io.micronaut.core.annotation.Introspected;

@io.micronaut.serde.annotation.Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    public String foo;
    public Used used;
}
@JsonIgnoreType
@io.micronaut.serde.annotation.Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Used {
    public String bar;
}
''')
        def bean = newInstance(compiled, 'example.Test')
        bean.foo = '42'
        bean.used = newInstance(compiled, 'example.Used')
        bean.used.bar = '56'

        expect:
        serializeToString(jsonMapper, bean) == '{"foo":"42"}'
        deserializeFromString(jsonMapper, bean.getClass(), '{"foo":"42","used":{"bar":"56"}}').used == null
    }
    // endregion
}
