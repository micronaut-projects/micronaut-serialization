package io.micronaut.serde.jackson.object

import com.fasterxml.jackson.annotation.JsonAutoDetect
import io.micronaut.json.JsonMapper
import io.micronaut.serde.exceptions.SerdeException
import io.micronaut.serde.jackson.JsonCompileSpec
import spock.lang.Issue
import spock.lang.PendingFeature

class ObjectSerdeSpec extends JsonCompileSpec {

    @Issue("https://github.com/micronaut-projects/micronaut-serialization/issues/202")
    void "test generic subtype handling"() {
        given:
        def context = buildContext("""package subtypes;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;
import java.util.Collections;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Animal {
}

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Cat extends Animal {
    final public int lives;
    Cat(int lives) {
        this.lives = lives;
    }
}

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Holder<A extends Animal> {
    public final Animal animalNonGeneric;
    public final List<Animal> animalsNonGeneric;
    public final A animal;
    public final List<A> animals;
    Holder(A animal) {
        this.animal = animal;
        this.animals = Collections.singletonList(animal);
        this.animalNonGeneric = animal;
        this.animalsNonGeneric = Collections.singletonList(animal);
    }
}
""")

        when:
        def cat = newInstance(context, 'subtypes.Cat', 9)
        def catHolder = newInstance(context, 'subtypes.Holder', cat)
        def catJson = writeJson(jsonMapper, catHolder)

        then:
        catJson == '{"animalNonGeneric":{},"animalsNonGeneric":[{}],"animal":{"lives":9},"animals":[{"lives":9}]}'

        cleanup:
        context.close()
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

}
