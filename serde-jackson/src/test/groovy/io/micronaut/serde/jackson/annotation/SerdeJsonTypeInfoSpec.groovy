package io.micronaut.serde.jackson.annotation

import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.serde.jackson.JsonTypeInfoSpec
import spock.lang.PendingFeature
import spock.lang.Unroll

class SerdeJsonTypeInfoSpec extends JsonTypeInfoSpec {

    @Override
    protected void configureContext(ApplicationContextBuilder contextBuilder) {
        super.configureContext(contextBuilder.properties(
                Map.of("micronaut.serde.deserialization.ignore-unknown", "false")
        ))
    }

    @Override
    protected boolean jacksonCustomOrder() {
        return false
    }

    void 'test wrapped subtype with @JsonTypeInfo(include = JsonTypeInfo.As.#include)'(String include) {
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
  public String foo;
  @JsonUnwrapped
  public Base base;
}

@Serdeable
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.$include, property = "type")
@JsonSubTypes(
    @JsonSubTypes.Type(value = Sub.class, name = "sub-class")
)
class Base {
    private String type;
    private String string;

    public Base(String string) {
        this.string = string;
    }

    public String getString() {
        return string;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
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
            if (include == "EXISTING_PROPERTY") {
                base.type = "sub-class"
            }
            def wrapper = newInstance(context, 'test.Wrapper')
            wrapper.foo = "bar"
            wrapper.base = base

            def result = writeJson(jsonMapper, wrapper)
        then:
            result

        when:
            def bean = jsonMapper.readValue(result, argumentOf(context, "test.Wrapper"))

        then:
            bean.foo == 'bar'
            bean.base.getClass().name == 'test.Sub'
            bean.base.string == 'a'
            bean.base.integer == 1

        cleanup:
            context.close()

        where:
            include << ["WRAPPER_OBJECT", "PROPERTY", "EXISTING_PROPERTY", "EXTERNAL_PROPERTY"]
    }

    void 'test wrapped subtype in constructor with @JsonTypeInfo(include = JsonTypeInfo.As.#include)'(String include) {
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
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.$include, property = "type")
@JsonSubTypes(
    @JsonSubTypes.Type(value = Sub.class, name = "sub-class")
)
class Base {
    private String type;
    private String string;

    public Base(String string) {
        this.string = string;
    }

    public String getString() {
        return string;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
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
            if (include == "EXISTING_PROPERTY") {
                base.type = "sub-class"
            }
            def wrapper = newInstance(context, 'test.Wrapper', "bar", base)

            def result = writeJson(jsonMapper, wrapper)
            def bean = jsonMapper.readValue(result, argumentOf(context, "test.Wrapper"))

        then:
            bean.foo == 'bar'
            bean.base.getClass().name == 'test.Sub'
            bean.base.string == 'a'
            bean.base.integer == 1

        cleanup:
            context.close()

        where:
            include << ["WRAPPER_OBJECT", "PROPERTY", "EXISTING_PROPERTY"]
    }

    void 'test wrapped subtype with @JsonTypeInfo(include = WRAPPER_ARRAY)'() {
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
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_ARRAY)
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
            def bean = jsonMapper.readValue(result, argumentOf(context, "test.Wrapper"))

        then:
            def e = thrown(Exception)
            e.message.contains "Sub doesn't support serializing into an existing object"

        cleanup:
            context.close()
    }

    // Unsupported Jackson Databind: Cannot define Creator property "name" as `@JsonUnwrapped`: combination not yet supported
    def 'test JsonTypeInfo with wrapper array in constructor and @JsonUnwrapped'() {
        given:
        def ctx = buildContext('example.Wrapper', '''
package example;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
record Wrapper(Base base, String other, @JsonUnwrapped Name name) {
}

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@JsonSubTypes({
    @JsonSubTypes.Type(value = A.class, name = "a"),
    @JsonSubTypes.Type(value = B.class, names = {"b", "c"})
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_ARRAY)
class Base {
}

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@Serdeable
class Name {
    public String fieldX;
    public String fieldY;
}

class A extends Base {
    public String fieldA;
}
class B extends Base {
    public String fieldB;
}
''', true)
        def wrapperClass = ctx.classLoader.loadClass('example.Wrapper')
        def name = newInstance(ctx, 'example.Name')
        name.fieldX = "X"
        name.fieldY = "Y"
        def a = newInstance(ctx, 'example.A')
        a.fieldA = 'foo'
        def wrapper = newInstance(ctx, 'example.Wrapper', a, "abc", name)

        expect:
        deserializeFromString(jsonMapper, wrapperClass, '{"base": ["a",{"fieldA":"foo"}], "other":"xyz"}').base.fieldA == 'foo'
        deserializeFromString(jsonMapper, wrapperClass, '{"base": ["b",{"fieldB":"foo"}], "other":"xyz"}').base.fieldB == 'foo'
        deserializeFromString(jsonMapper, wrapperClass, '{"base": ["c",{"fieldB":"foo"}], "other":"xyz"}').base.fieldB == 'foo'
        deserializeFromString(jsonMapper, wrapperClass, '{"base": ["c",{"fieldB":"foo"}], "other":"xyz","fieldX":"ABC"}').name.fieldX == 'ABC'

        serializeToString(jsonMapper, wrapper) == '{"base":["a",{"fieldA":"foo"}],"other":"abc","fieldX":"X","fieldY":"Y"}'

        cleanup:
        ctx.close()
    }

    @PendingFeature(reason = "JsonTypeInfo.Id.DEDUCTION not supported")
    def 'test JsonTypeInfo with deduction'() {
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
    def 'test JsonTypeInfo with deduction with supertype prop'() {
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
    def 'test JsonTypeInfo with deduction unwrapped'() {
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

    @Unroll
    void "test fail compilation on unsupported 'use' #use"() {
        when:
        buildBeanIntrospection('subtypeerrors.Test', """
package subtypeerrors;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@JsonTypeInfo(
  use = JsonTypeInfo.Id.${use.name()},
  include = JsonTypeInfo.As.PROPERTY,
  property = "type")
class Test {
    public String name;
}
""")
        then:
        def e = thrown(RuntimeException)
        e.message.contains(" Unsupported JsonTypeInfo use: " + use.name())

        where:
        use << [JsonTypeInfo.Id.DEDUCTION, JsonTypeInfo.Id.CUSTOM]
    }

    void "test default implementation - with @DefaultImplementation"() {
        given:
            def context = buildContext("""
package defaultimpl;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.context.annotation.DefaultImplementation;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@DefaultImplementation(Dog.class)
@Serdeable
interface Animal {
    String getName();
}

@Serdeable
class Dog implements Animal {
    private double barkVolume;
    private String name;
    @Override public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public void setBarkVolume(double barkVolume) {
        this.barkVolume = barkVolume;
    }
    public double getBarkVolume() {
        return barkVolume;
    }
}

""")

        when:
            def dog = newInstance(context, 'defaultimpl.Dog', [name:"Fred", barkVolume:1.1d])
            def dogJson = writeJson(jsonMapper, dog)

        then:
            dogJson == '{"name":"Fred","barkVolume":1.1}'

        when:"No discriminator is used the default impl is chosen"
            def dogClass = dog.getClass()
            def dogBean = jsonMapper.readValue(dogJson, argumentOf(context, 'defaultimpl.Animal'))


        then:
            dogClass.isInstance(dogBean)
            dogBean.name == "Fred"
            dogBean.barkVolume == 1.1d
        cleanup:
            context.close()
    }

    void "test default implementation - with @Serdeable.Deserializable(as)"() {
        given:
            def context = buildContext("""
package defaultimpl;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.context.annotation.DefaultImplementation;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable.Deserializable(as = Dog.class)
@Serdeable.Serializable
interface Animal {
    String getName();
}

@Serdeable
class Dog implements Animal {
    private String name;
    private double barkVolume;

    @Override
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public void setBarkVolume(double barkVolume) {
        this.barkVolume = barkVolume;
    }
    public double getBarkVolume() {
        return barkVolume;
    }
}

""")

        when:
            def dog = newInstance(context, 'defaultimpl.Dog', [name:"Fred", barkVolume:1.1d])
            def dogJson = writeJson(jsonMapper, dog)

        then:
            dogJson == '{"name":"Fred","barkVolume":1.1}'

        when:"No discriminator is used the default impl is chosen"
            def dogClass = dog.getClass()
            def dogBean = jsonMapper.readValue(dogJson, argumentOf(context, 'defaultimpl.Animal'))

        then:
            dogClass.isInstance(dogBean)
            dogBean.name == "Fred"
            dogBean.barkVolume == 1.1d
        cleanup:
            context.close()
    }

     void "test subtype binding as property -- immutable types"() {
        given:
        def context = buildContext("""
package subtypes;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME,
  include = JsonTypeInfo.As.PROPERTY,
  property = "type")
 @JsonSubTypes({
  @JsonSubTypes.Type(Dog.class),
  @JsonSubTypes.Type(Cat.class)
})
class Animal {
    public String name;
}

@JsonTypeName("dog")
class Dog extends Animal {
    public final double barkVolume;

    @JsonCreator
    Dog(@JsonProperty("barkVolume") double barkVolume) {
        this.barkVolume = barkVolume;
    }
}

@JsonTypeName("cat")
class Cat extends Animal {
    public boolean likesCream;
    final public int lives;

    @JsonCreator
    Cat(@JsonProperty("name") String name, @JsonProperty("lives") int lives) {
        this.name = name;
        this.lives = lives;
    }
}
""")

        when:
        def dog = newInstance(context, 'subtypes.Dog', 1.1d)
        dog.name = "Fred"
        def cat = newInstance(context, 'subtypes.Cat', "Joe", 9)
        cat.likesCream = true
        def dogJson = writeJson(jsonMapper, dog)
        def catJson = writeJson(jsonMapper, cat)

        then:
        dogJson == '{"type":"dog","name":"Fred","barkVolume":1.1}'
        catJson == '{"type":"cat","name":"Joe","likesCream":true,"lives":9}'

        when:
        def dogClass = dog.getClass()
        def catClass = cat.getClass()
        def dogBean = jsonMapper.readValue(dogJson, argumentOf(context, 'subtypes.Animal'))
        def catBean = jsonMapper.readValue(catJson, argumentOf(context, 'subtypes.Animal'))


        then:
        catClass.isInstance(catBean)
        dogClass.isInstance(dogBean)
        dogBean.name == "Fred"
        dogBean.barkVolume == 1.1d
        catBean.name == "Joe"
        catBean.likesCream
        catBean.lives == 9

        when:"the buffer is used"
        dogBean = jsonMapper.readValue('{"name":"Fred","barkVolume":1.1,"type":"dog"}', argumentOf(context, 'subtypes.Animal'))
        catBean = jsonMapper.readValue('{"name":"Joe","likesCream":true,"lives":9,"type":"cat"}', argumentOf(context, 'subtypes.Animal'))

        then:
        catClass.isInstance(catBean)
        dogClass.isInstance(dogBean)
        dogBean.name == "Fred"
        dogBean.barkVolume == 1.1d
        catBean.name == "Joe"
        catBean.likesCream
        catBean.lives == 9

        when:"the buffer is used again"
        dogBean = jsonMapper.readValue('{"name":"Fred","type":"dog","barkVolume":1.1}', argumentOf(context, 'subtypes.Animal'))
        catBean = jsonMapper.readValue('{"name":"Joe","type":"cat","likesCream":true,"lives":9}', argumentOf(context, 'subtypes.Animal'))

        then:
        catClass.isInstance(catBean)
        dogClass.isInstance(dogBean)
        dogBean.name == "Fred"
        dogBean.barkVolume == 1.1d
        catBean.name == "Joe"
        catBean.likesCream
        catBean.lives == 9

        cleanup:
        context.close()
    }

    void "test nested subtypes"() {
        given:
        def context = buildContext("""
package subtypes;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME,
  include = JsonTypeInfo.As.PROPERTY,
  property = "type")
abstract class Animal {
    public String name;
}

@JsonTypeName("dog")
class Dog extends Animal {
    public final double barkVolume;
    public Animal friend;

    @JsonCreator
    Dog(@JsonProperty("barkVolume") double barkVolume, @JsonProperty("friend") Animal friend) {
        this.barkVolume = barkVolume;
        this.friend = friend;
    }
}

@JsonTypeName("cat")
class Cat extends Animal {
    public boolean likesCream;
    final public int lives;

    @JsonCreator
    Cat(@JsonProperty("name") String name, @JsonProperty("lives") int lives) {
        this.name = name;
        this.lives = lives;
    }
}
""")

        when:
        def cat = newInstance(context, 'subtypes.Cat', "Joe", 9)
        cat.likesCream = true
        def dog = newInstance(context, 'subtypes.Dog', 1.1d, cat)
        dog.name = "Fred"
        def dogJson = writeJson(jsonMapper, dog)
        def catJson = writeJson(jsonMapper, cat)

        then:
        dogJson == '{"type":"dog","name":"Fred","barkVolume":1.1,"friend":{"type":"cat","name":"Joe","likesCream":true,"lives":9}}'
        catJson == '{"type":"cat","name":"Joe","likesCream":true,"lives":9}'

        when:
        def readDog = jsonMapper.readValue(dogJson, argumentOf(context, 'subtypes.Animal'))

        then:
        readDog.getClass().name.contains(".Dog")
        readDog.friend.getClass().name.contains("Cat")

        cleanup:
        context.close()
    }
}
