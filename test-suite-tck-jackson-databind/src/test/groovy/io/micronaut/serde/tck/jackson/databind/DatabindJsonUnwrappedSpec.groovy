package io.micronaut.serde.tck.jackson.databind

import io.micronaut.core.type.Argument
import io.micronaut.serde.jackson.JsonUnwrappedSpec
import spock.lang.PendingFeature

class DatabindJsonUnwrappedSpec extends JsonUnwrappedSpec {

     // This cases are not supported by Jackson

    void "test @JsonUnwrapped records"() {
        given:
        def context = buildContext("""
package unwrapped;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
record Parent(
  int age,
  @JsonUnwrapped
  Name name) {
}

@Serdeable
record Name(
  String first, String last
) {}
""")

        when:
        def name = newInstance(context, 'unwrapped.Name', "Fred", "Flinstone")
        def parent = newInstance(context, 'unwrapped.Parent', 10, name)

        def result = writeJson(jsonMapper, parent)

        then:
        result == '{"age":10,"first":"Fred","last":"Flinstone"}'

        when:
        def read = jsonMapper.readValue(result, Argument.of(context.classLoader.loadClass('unwrapped.Parent')))

        then:
        read.age == 10
        read.name.first == 'Fred'
        read.name.last == "Flinstone"

        cleanup:
        context.close()
    }

    @PendingFeature
    void "test @JsonUnwrapped - parent constructor args"() {
        given:
        def context = buildContext("""
package unwrapped;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Parent {
  public final int age;
  @JsonUnwrapped
  public final Name name;

  Parent(int age, @JsonUnwrapped Name name) {
      this.age = age;
      this.name = name;
  }
}

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Name {
  public final String first, last;
  Name(String first, String last) {
      this.first = first;
      this.last = last;
  }
}
""")

        when:
        def name = newInstance(context, 'unwrapped.Name', "Fred", "Flinstone")
        def parent = newInstance(context, 'unwrapped.Parent', 10, name)

        def result = writeJson(jsonMapper, parent)

        then:
        result == '{"age":10,"first":"Fred","last":"Flinstone"}'

        when:
        def read = jsonMapper.readValue(result, Argument.of(context.classLoader.loadClass('unwrapped.Parent')))

        then:
        read.age == 10
        read.name.first == 'Fred'
        read.name.last == "Flinstone"

        cleanup:
        context.close()
    }

    @PendingFeature
    void 'test wrapped subtype with property info'() {
        given:
            def context = buildContext('test.Base', """
package test;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Wrapper {
  public final String foo;
  @JsonUnwrapped
  public final Base base;

  Wrapper(String foo, @JsonUnwrapped Base base) {
      this.base = base;
      this.foo = foo;
  }
}

@Serdeable
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    @JsonSubTypes.Type(value = Sub.class, name = "sub-class")
)
class Base {
    private String string;

    public Base(String string) {
        this.string = string;
    }

    public String getString() {
        return string;
    }
}

@Serdeable
class Sub extends Base {
    private Integer integer;

    public Sub(String string, Integer integer) {
        super(string);
        this.integer = integer;
    }

    public Integer getInteger() {
        return integer;
    }
}
""")
        when:
            def base = newInstance(context, 'test.Sub', "a", 1)
            def wrapper = newInstance(context, 'test.Wrapper', "bar", base)

            def result = writeJson(jsonMapper, wrapper)

        then:
            result == '{"foo":"bar","type":"sub-class","string":"a","integer":1}'

        when:
            result = jsonMapper.readValue(result, argumentOf(context, "test.Wrapper"))

        then:
            result.foo == 'bar'
            result.base.getClass().name == 'test.Sub'
            result.base.string == 'a'
            result.base.integer == 1

        when:
            result = jsonMapper.readValue('{"string":"a","integer":1,"type":"sub-class","foo":"bar"}', argumentOf(context, "test.Wrapper"))

        then:
            result.foo == 'bar'
            result.base.getClass().name == 'test.Sub'
            result.base.string == 'a'
            result.base.integer == 1

        when:
            result = jsonMapper.readValue('{"foo":"bar", "type":"some-other-type","string":"a","integer":1}', argumentOf(context, "test.Wrapper"))

        then:
            result.getClass().name != 'test.Sub'

        when:
            result = jsonMapper.readValue('{"string":"a","integer":1,"foo":"bar","type":"Sub"}', argumentOf(context, "test.Wrapper"))

        then:
            result.getClass().name != 'test.Sub'
    }

    @PendingFeature
    void 'test wrapped subtype with wrapper info'() {
        given:
            def context = buildContext('test.Base', """
package test;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Wrapper {
  public final String foo;
  @JsonUnwrapped
  public final Base base;

  Wrapper(String foo, @JsonUnwrapped Base base) {
      this.base = base;
      this.foo = foo;
  }
}

@Serdeable
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
@JsonSubTypes(
    @JsonSubTypes.Type(value = Sub.class, name = "subClass")
)
class Base {
    private String string;

    public Base(String string) {
        this.string = string;
    }

    public String getString() {
        return string;
    }
}

@Serdeable
class Sub extends Base {
    private Integer integer;

    public Sub(String string, Integer integer) {
        super(string);
        this.integer = integer;
    }

    public Integer getInteger() {
        return integer;
    }
}
""")
        when:
            def result = jsonMapper.readValue('{"foo":"bar","subClass":{"string":"a","integer":1}}', argumentOf(context, "test.Wrapper"))

        then:
            result.foo == 'bar'
            result.base.getClass().name == 'test.Sub'
            result.base.string == 'a'
            result.base.integer == 1

        when:
            result = jsonMapper.readValue('{"subClass":{"string":"a","integer":1}, "foo":"bar"}', argumentOf(context, "test.Wrapper"))

        then:
            result.foo == 'bar'
            result.base.getClass().name == 'test.Sub'
            result.base.string == 'a'
            result.base.integer == 1

        when:
            def json = writeJson(jsonMapper, result)

        then:
            json == '{"foo":"bar","subClass":{"string":"a","integer":1}}'
    }

}
