package io.micronaut.json.generated


import com.fasterxml.jackson.core.JsonFactory
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument

import java.nio.charset.StandardCharsets

class GeneratedObjectCodecSpec extends AbstractTypeElementSpec {
    def readValue() {
        when:
        def ctx = buildContext('example.TestCls', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.json.annotation.*;

@SerializableBean
public class TestCls {
    public final String foo;

    @JsonCreator
    TestCls(@JsonProperty("foo") String foo) {
        this.foo = foo;
    }
}
''', true)
        def codec = ctx.getBean(GeneratedObjectMapper)
        def factory = new JsonFactory()

        then:
        codec.readValue('{"foo":"bar"}', Argument.of(ctx.classLoader.loadClass('example.TestCls'))).foo == 'bar'
    }

    def writeValueAsBytes() {
        when:
        def ctx = buildContext('example.TestCls', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.json.annotation.*;

@SerializableBean
public class TestCls {
    public final String foo;

    @JsonCreator
    TestCls(@JsonProperty("foo") String foo) {
        this.foo = foo;
    }
}
''', true)
        def codec = ctx.getBean(GeneratedObjectMapper)

        then:
        new String(codec.writeValueAsBytes(ctx.classLoader.loadClass('example.TestCls').newInstance("bar")), StandardCharsets.UTF_8) == '{"foo":"bar"}'
    }

    def "super type serializable"() {
        given:
        def ctx = buildContext('example.Base', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.json.annotation.*;

@SerializableBean
public class Base {
    public String foo;
}

class Subclass extends Base {
    public String bar;
}
''', true)
        def codec = ctx.getBean(GeneratedObjectMapper)

        def value = ctx.classLoader.loadClass('example.Subclass').newInstance()
        value.foo = "42"
        value.bar = "24"

        when:
        def bytes = codec.writeValueAsBytes(value)

        then:"use the serializer for the base class"
        new String(bytes, StandardCharsets.UTF_8) == '{"foo":"42"}'

        //then:"to avoid confusion because of missing subclass properties, don't allow serialization"
        //thrown Exception
    }

    def "generic bean deser"() {
        given:
        def ctx = buildContext('example.GenericBean', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.json.annotation.*;
import java.util.*;

@SerializableBean
public class GenericBean<T> {
    public T naked;
    public List<T> list;
}
''', true)
        def codec = ctx.getBean(GeneratedObjectMapper)

        when:
        def parsed = codec.readValue('{"naked":"foo","list":["bar"]}', Argument.of(ctx.classLoader.loadClass('example.GenericBean'), String.class))

        then:
        parsed.naked == 'foo'
        parsed.list == ['bar']
    }

    def "generic bean ser"() {
        given:
        def ctx = buildContext('example.GenericBean', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.json.annotation.*;
import java.util.*;

@SerializableBean
public class GenericBean<T> {
    public T naked;
    public List<T> list;
}

@SerializableBean
class GenericBeanWrapper {
    @JsonValue public GenericBean<String> bean;
}
''', true)
        def codec = ctx.getBean(GeneratedObjectMapper)

        def bean = ctx.classLoader.loadClass('example.GenericBean').newInstance()
        bean.naked = "foo"
        bean.list = ["bar"]
        def wrapper = ctx.classLoader.loadClass('example.GenericBeanWrapper').newInstance()
        wrapper.bean = bean

        when:
        // there's currently no way to pass the type to write, and because of type erasure the framework can't know that
        // bean is a GenericBean<String>, so we use a wrapper (with @JsonValue) that has the full type.
        def json = new String(codec.writeValueAsBytes(wrapper), StandardCharsets.UTF_8)

        then:
        json == '{"naked":"foo","list":["bar"]}'
    }


    def "raw map"() {
        given:
        def ctx = ApplicationContext.run()
        def codec = ctx.getBean(GeneratedObjectMapper)

        when:
        def parsed = codec.readValue('{"string":"foo","list":["bar"]}', Argument.of(Map.class))

        then:
        parsed == [string: 'foo', list: ['bar']]

        when:
        def json = new String(codec.writeValueAsBytes([string: 'foo', list: ['bar']]), StandardCharsets.UTF_8)

        then:
        json == '{"string":"foo","list":["bar"]}'
    }

    def "top-level null"() {
        given:
        def ctx = ApplicationContext.run()
        def codec = ctx.getBean(GeneratedObjectMapper)

        when:
        def parsedNaked = codec.readValue('null', Argument.of(String.class))
        then:
        parsedNaked == null

        when:
        def parsedOpt = codec.readValue('null', Argument.of(Optional.class, String.class))
        then:
        parsedOpt == Optional.empty()
    }

    def "Map<Object, V>"() {
        given:
        def ctx = ApplicationContext.run()
        def codec = ctx.getBean(GeneratedObjectMapper)

        when:
        def parsed = codec.readValue('{"foo":42}', Argument.mapOf(Object, Integer))
        then:
        parsed == [foo: 42]
    }

    def 'views'() {
        given:
        def ctx = buildContext('example.WithViews', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.json.annotation.*;

@SerializableBean
@JsonView(Views.Public.class)
public class WithViews {
    public String firstName;
    public String lastName;
    @JsonView(Views.Internal.class)
    public String birthdate;
    @JsonView(Views.Admin.class)
    public String password; // don't do plaintext passwords at home please
}

class Views {
    static class Public {}

    static class Internal extends Public {}

    static class Admin extends Internal {}
}
''', true)
        def codec = ctx.getBean(GeneratedObjectMapper)

        def WithViews = ctx.classLoader.loadClass('example.WithViews')
        def withViews = WithViews.newInstance()
        withViews.firstName = 'Bob'
        withViews.lastName = 'Jones'
        withViews.birthdate = '08/01/1980'
        withViews.password = 'secret'

        def Public = ctx.classLoader.loadClass('example.Views$Public')
        def Internal = ctx.classLoader.loadClass('example.Views$Internal')
        def Admin = ctx.classLoader.loadClass('example.Views$Admin')

        expect:
        new String(codec.cloneWithViewClass(Admin).writeValueAsBytes(withViews), StandardCharsets.UTF_8) ==
                '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}'
        new String(codec.cloneWithViewClass(Internal).writeValueAsBytes(withViews), StandardCharsets.UTF_8) ==
                '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980"}'
        new String(codec.cloneWithViewClass(Public).writeValueAsBytes(withViews), StandardCharsets.UTF_8) ==
                '{"firstName":"Bob","lastName":"Jones"}'
        new String(codec.writeValueAsBytes(withViews), StandardCharsets.UTF_8) == '{}'

        codec.readValue('{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', Argument.of(WithViews))
                .firstName == null

        codec.cloneWithViewClass(Public).readValue('{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', Argument.of(WithViews))
                .firstName == 'Bob'
        codec.cloneWithViewClass(Public).readValue('{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', Argument.of(WithViews))
                .birthdate == null

        codec.cloneWithViewClass(Internal).readValue('{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', Argument.of(WithViews))
                .firstName == 'Bob'
        codec.cloneWithViewClass(Internal).readValue('{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', Argument.of(WithViews))
                .birthdate == '08/01/1980'
        codec.cloneWithViewClass(Internal).readValue('{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', Argument.of(WithViews))
                .password == null

        codec.cloneWithViewClass(Admin).readValue('{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', Argument.of(WithViews))
                .firstName == 'Bob'
        codec.cloneWithViewClass(Admin).readValue('{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', Argument.of(WithViews))
                .birthdate == '08/01/1980'
        codec.cloneWithViewClass(Admin).readValue('{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', Argument.of(WithViews))
                .password == 'secret'
    }

    def 'custom serializer does not collide with native serializers'() {
        // note: this test isnt very robust, because SerializerLocator may use the 'right' Serializer for String even
        // when UpperCaseSer would be eligible.

        given:
        def ctx = buildContext('example.CustomSerializerBean', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.json.annotation.*;
import io.micronaut.json.*;
import io.micronaut.context.annotation.*;
import jakarta.inject.*;
import java.io.*;
import java.util.*;

@SerializableBean
public class CustomSerializerBean {
    public String foo;
    @CustomSerializer(serializer = UpperCaseSer.class)
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
''', true)
        def codec = ctx.getBean(GeneratedObjectMapper)

        def bean = ctx.classLoader.loadClass('example.CustomSerializerBean').newInstance()
        bean.foo = 'boo'
        bean.bar = 'Baz'
        expect:
        new String(codec.writeValueAsBytes(bean), StandardCharsets.UTF_8) == '{"foo":"boo","bar":"BAZ"}'
        new String(codec.writeValueAsBytes('Baz'), StandardCharsets.UTF_8) == '"Baz"'
    }
}
