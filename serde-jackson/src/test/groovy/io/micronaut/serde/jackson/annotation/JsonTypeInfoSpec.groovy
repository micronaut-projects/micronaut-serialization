package io.micronaut.serde.jackson.annotation

import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.core.type.Argument
import io.micronaut.serde.jackson.JsonCompileSpec
import io.micronaut.serde.jackson.data.Bird
import io.micronaut.serde.jackson.data.ColorEnum
import io.micronaut.serde.jackson.data.Mammal
import io.micronaut.serde.jackson.data.Reptile
import spock.lang.Unroll

class JsonTypeInfoSpec extends JsonCompileSpec {

    @Override
    protected void configureContext(ApplicationContextBuilder contextBuilder) {
        super.configureContext(contextBuilder.properties(
                Map.of("micronaut.serde.deserialization.ignore-unknown", "false")
        ))
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

    void "test default implementation - with @Deserializable(as)"() {
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

    void "test default implementation"() {
        given:
        def context = buildContext("""
package defaultimpl;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME,
  property = "type",
  defaultImpl = Dog.class)
interface Animal {
    String getName();
}

@JsonTypeName("dog")
class Dog implements Animal {
    public double barkVolume;
    private String name;
    @Override public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
}

@JsonTypeName("cat")
class Cat implements Animal {
    public boolean likesCream;
    public int lives;
    private String name;
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
}
""")

        when:
        def dog = newInstance(context, 'defaultimpl.Dog', [name:"Fred", barkVolume:1.1d])
        def cat = newInstance(context, 'defaultimpl.Cat', [name:"Joe", likesCream:true, lives: 9])
        def dogJson = writeJson(jsonMapper, dog)
        def catJson = writeJson(jsonMapper, cat)

        then:
        dogJson == '{"type":"dog","name":"Fred","barkVolume":1.1}'
        catJson == '{"type":"cat","name":"Joe","likesCream":true,"lives":9}'

        when:"No discriminator is used the default impl is chosen"
        def dogClass = dog.getClass()
        def catClass = cat.getClass()
        def dogBean = jsonMapper.readValue('{"name":"Fred","barkVolume":1.1}', argumentOf(context, 'defaultimpl.Animal'))
        def catBean = jsonMapper.readValue(catJson, argumentOf(context, 'defaultimpl.Animal'))


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

    void "test MINIMAL_CLASS implementation"() {
        given:
        def context = buildContext("""
package test;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS, defaultImpl = Dog.class)
interface Animal {
    String getName();
}

@Serdeable
class Dog implements Animal {
    private double barkVolume;
    private String name;
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

@Serdeable
class Cat implements Animal {
    private boolean likesCream;
    private int lives;
    private String name;
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public int getLives() {
        return lives;
    }
    public void setLives(int lives) {
        this.lives = lives;
    }
    public void setLikesCream(boolean likesCream) {
        this.likesCream = likesCream;
    }
    public boolean isLikesCream() {
        return likesCream;
    }
}
""")

        when:
        def dog = newInstance(context, 'test.Dog', [name:"Fred", barkVolume:1.1d])
        def cat = newInstance(context, 'test.Cat', [name:"Joe", likesCream:true, lives: 9])
        def dogJson = writeJson(jsonMapper, dog)
        def catJson = writeJson(jsonMapper, cat)

        then:
        dogJson == '{"@c":".Dog","name":"Fred","barkVolume":1.1}'
        catJson == '{"@c":".Cat","name":"Joe","lives":9,"likesCream":true}'

        when:
        def dogBean = jsonMapper.readValue(dogJson, argumentOf(context, 'test.Animal'))
        def catBean = jsonMapper.readValue(catJson, argumentOf(context, 'test.Animal'))

        then:
        dog.getClass().isInstance(dogBean)
        cat.getClass().isInstance(catBean)
        dogBean.name == "Fred"
        dogBean.barkVolume == 1.1d
        catBean.name == "Joe"
        catBean.likesCream
        catBean.lives == 9

        when:"No discriminator is used the default impl is chosen"
        dogBean = jsonMapper.readValue('{"name":"Fred","barkVolume":1.1}', argumentOf(context, 'test.Animal'))
        catBean = jsonMapper.readValue('{"@c":".Cat","name":"Joe","lives":9,"likesCream":true}', argumentOf(context, 'test.Animal'))

        then:
        cat.getClass().isInstance(catBean)
        dog.getClass().isInstance(dogBean)
        dogBean.name == "Fred"
        dogBean.barkVolume == 1.1d
        catBean.name == "Joe"
        catBean.likesCream
        catBean.lives == 9

        cleanup:
        context.close()
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

    @Unroll
    void "test fail compilation on unsupported 'include' #include"() {
        when:
        buildBeanIntrospection('subtypeerrors.Test', """
package subtypeerrors;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@JsonTypeInfo(
  use = JsonTypeInfo.Id.CLASS,
  include = JsonTypeInfo.As.$include,
  property = "type")
class Test {
    public String name;
}
""")
        then:
        def e = thrown(RuntimeException)
        e.message.contains("Only 'include' of type PROPERTY or WRAPPER_OBJECT are supported")

        where:
        include << JsonTypeInfo.As.values() - [JsonTypeInfo.As.PROPERTY, JsonTypeInfo.As.WRAPPER_OBJECT]
    }

    void "test subtype binding as property for interface"() {
        given:
        def context = buildContext("""
package subtypes;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME,
  property = "type")
interface Animal {
    String getName();
}

@JsonTypeName("dog")
class Dog implements Animal {
    public double barkVolume;
    private String name;
    @Override public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
}

@JsonTypeName("cat")
class Cat implements Animal {
    public boolean likesCream;
    public int lives;
    private String name;
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
}
""")

        when:
        def dog = newInstance(context, 'subtypes.Dog', [name:"Fred", barkVolume:1.1d])
        def cat = newInstance(context, 'subtypes.Cat', [name:"Joe", likesCream:true, lives: 9])
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
        dogBean = jsonMapper.readValue('{"barkVolume":1.1,"name":"Fred","type":"dog"}', argumentOf(context, 'subtypes.Animal'))
        catBean = jsonMapper.readValue('{"likesCream":true,"lives":9,"name":"Joe","type":"cat"}', argumentOf(context, 'subtypes.Animal'))

        then:
        dogClass.isInstance(dogBean)
        dogBean.name == "Fred"
        dogBean.barkVolume == 1.1d
        catBean.name == "Joe"
        catBean.likesCream
        catBean.lives == 9

        cleanup:
        context.close()
    }

    void "test subtype binding as property"() {
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
class Animal {
    public String name;
}

@JsonTypeName("dog")
class Dog extends Animal {
    public double barkVolume;
}

@JsonTypeName("cat")
class Cat extends Animal {
    public boolean likesCream;
    public int lives;
}
""")

        when:
        def dog = newInstance(context, 'subtypes.Dog', [name:"Fred", barkVolume:1.1d])
        def cat = newInstance(context, 'subtypes.Cat', [name:"Joe", likesCream:true, lives: 9])
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
        dogBean = jsonMapper.readValue('{"barkVolume":1.1,"name":"Fred","type":"dog"}', argumentOf(context, 'subtypes.Animal'))
        catBean = jsonMapper.readValue('{"likesCream":true,"lives":9,"name":"Joe","type":"cat"}', argumentOf(context, 'subtypes.Animal'))

        then:
        dogClass.isInstance(dogBean)
        dogBean.name == "Fred"
        dogBean.barkVolume == 1.1d
        catBean.name == "Joe"
        catBean.likesCream
        catBean.lives == 9

        cleanup:
        context.close()
    }

    void "test subtype binding as wrapper object"() {
        given:
        def context = buildContext("""
package subtypes;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME,
  include = JsonTypeInfo.As.WRAPPER_OBJECT,
  property = "type")
class Animal {
    public String name;
}

@JsonTypeName("dog")
class Dog extends Animal {
    public double barkVolume;
}

@JsonTypeName("cat")
class Cat extends Animal {
    public boolean likesCream;
    public int lives;
}
""")

        when:
        def dog = newInstance(context, 'subtypes.Dog', [name:"Fred", barkVolume:1.1d])
        def cat = newInstance(context, 'subtypes.Cat', [name:"Joe", likesCream:true, lives: 9])
        def dogJson = writeJson(jsonMapper, dog)
        def catJson = writeJson(jsonMapper, cat)

        then:
        dogJson == '{"dog":{"name":"Fred","barkVolume":1.1}}'
        catJson == '{"cat":{"name":"Joe","likesCream":true,"lives":9}}'

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
        dogBean = jsonMapper.readValue('{"dog":{"barkVolume":1.1,"name":"Fred"}}', argumentOf(context, 'subtypes.Animal'))
        catBean = jsonMapper.readValue('{"cat":{"likesCream":true,"lives":9,"name":"Joe"}}', argumentOf(context, 'subtypes.Animal'))

        then:
        dogClass.isInstance(dogBean)
        dogBean.name == "Fred"
        dogBean.barkVolume == 1.1d
        catBean.name == "Joe"
        catBean.likesCream
        catBean.lives == 9

        cleanup:
        context.close()
    }

    void "test subtype binding as property - use CLASS"() {
        given:
        def context = buildContext("""
package subtypes;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@JsonTypeInfo(
  use = JsonTypeInfo.Id.CLASS,
  include = JsonTypeInfo.As.PROPERTY)
@Serdeable
class Animal {
    private String name;
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
}

@Serdeable
class Dog extends Animal {
    private double barkVolume;
    public double getBarkVolume() {
        return barkVolume;
    }
    public void setBarkVolume(double barkVolume) {
        this.barkVolume = barkVolume;
    }
}

@Serdeable
class Cat extends Animal {
    private boolean likesCream;
    private int lives;

    public void setLikesCream(boolean likesCream) {
        this.likesCream = likesCream;
    }
    public boolean isLikesCream() {
        return likesCream;
    }
    public void setLives(int lives) {
        this.lives = lives;
    }
    public int getLives() {
        return lives;
    }
}
""")

        when:
        def dog = newInstance(context, 'subtypes.Dog', [name:"Fred", barkVolume:1.1d])
        def cat = newInstance(context, 'subtypes.Cat', [name:"Joe", likesCream:true, lives: 9])
        def dogJson = writeJson(jsonMapper, dog)
        def catJson = writeJson(jsonMapper, cat)

        then:
        dogJson == '{"@class":"subtypes.Dog","name":"Fred","barkVolume":1.1}'
        catJson == '{"@class":"subtypes.Cat","name":"Joe","likesCream":true,"lives":9}'

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
        dogBean = jsonMapper.readValue('{"name":"Fred","barkVolume":1.1,"@class":"subtypes.Dog"}', argumentOf(context, 'subtypes.Animal'))
        catBean = jsonMapper.readValue('{"name":"Joe","likesCream":true,"lives":9,"@class":"subtypes.Cat"}', argumentOf(context, 'subtypes.Animal'))

        then:
        dogClass.isInstance(dogBean)
        dogBean.name == "Fred"
        dogBean.barkVolume == 1.1d
        catBean.name == "Joe"
        catBean.likesCream
        catBean.lives == 9

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
class Animal {
    public String name;
}

@JsonTypeName("dog")
class Dog extends Animal {
    public final double barkVolume;
    Dog(double barkVolume) {
        this.barkVolume = barkVolume;
    }
}

@JsonTypeName("cat")
class Cat extends Animal {
    public boolean likesCream;
    final public int lives;
    Cat(String name, int lives) {
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
    Dog(double barkVolume, Animal friend) {
        this.barkVolume = barkVolume;
        this.friend = friend;
    }
}

@JsonTypeName("cat")
class Cat extends Animal {
    public boolean likesCream;
    final public int lives;
    Cat(String name, int lives) {
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

    void "test polymorphism"() {
        given:
        def context = buildContext("""
package subtypes;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Introspected
@Serdeable
record TypeB (String b) implements InterfaceB { }

@JsonSubTypes({
    @JsonSubTypes.Type(value=TypeB.class, name="typeB"),
})
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.WRAPPER_OBJECT
)
interface InterfaceB { }

@Introspected
@Serdeable
record TypeA (String a, InterfaceB b) { }
""")

        when:
        def l = List.of(
                newInstance(context, 'subtypes.TypeA', "a", newInstance(context, 'subtypes.TypeB', "x")),
                newInstance(context, 'subtypes.TypeA', "b", newInstance(context, 'subtypes.TypeB', "x"))
        )

        def json = writeJson(jsonMapper, l)

        then:
        json == """[{"a":"a","b":{"typeB":{"b":"x"}}},{"a":"b","b":{"typeB":{"b":"x"}}}]"""

        when:
        def list = jsonMapper.readValue(json, Argument.listOf(argumentOf(context, 'subtypes.TypeA')))

        then:
        list[0].a ==  "a"
        list[0].b.b == "x"
        list[1].a ==  "b"
        list[1].b.b == "x"

        cleanup:
        context.close()
    }

    void "test no properties subtype"() {
        given:
        def context = buildContext("""
package subtypes;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "validation-type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = NonEmptyString.class, name = "NonEmptyString"),
})
interface Validator { }

@Serdeable
class NonEmptyString implements Validator {
 }
""")

        def argument = argumentOf(context, 'subtypes.NonEmptyString')

        when:
        def bean = newInstance(context, 'subtypes.NonEmptyString')
        def json = writeJson(jsonMapper, bean)

        then:
        json == """{"validation-type":"NonEmptyString"}"""


        when:
        def deser = jsonMapper.readValue(json, argument)

        then:
        deser
        argument.isInstance(deser)

        cleanup:
        context.close()
    }

    void "test @JsonTypeInfo(visible=true)"() {
        given:
            def context = buildContext("""
package test;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME,
  property = "type",
  visible = true)
interface Animal {
    String getName();
}

@JsonTypeName("dog")
class Dog implements Animal {

    public double barkVolume;
    private String name;
    public String type;

    @Override
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
}

@JsonTypeName("cat")
class Cat implements Animal {
    public boolean likesCream;
    public String type;
    public int lives;
    private String name;
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
}
""")

        when:
            def dog = newInstance(context, 'test.Dog', [name:"Fred", barkVolume:1.1d])
            def cat = newInstance(context, 'test.Cat', [name:"Joe", likesCream:true, lives: 9])
            def dogJson = writeJson(jsonMapper, dog)
            def catJson = writeJson(jsonMapper, cat)

        then:
            dogJson == '{"type":"dog","name":"Fred","barkVolume":1.1}'
            catJson == '{"type":"cat","name":"Joe","likesCream":true,"lives":9}'

        when:
            def dog2 = jsonMapper.readValue(dogJson, argumentOf(context, 'test.Dog'))
            def cat2 = jsonMapper.readValue(catJson, argumentOf(context, 'test.Cat'))

        then:
            dog.class.isInstance(dog2)
            cat.class.isInstance(cat2)
            dog2.name == "Fred"
            dog2.barkVolume == 1.1d
            dog2.type == "dog"
            cat2.name == "Joe"
            cat2.likesCream
            cat2.lives == 9
            cat2.type == "cat"

        when:
            def dog3 = jsonMapper.readValue(dogJson, argumentOf(context, 'test.Animal'))
            def cat3 = jsonMapper.readValue(catJson, argumentOf(context, 'test.Animal'))

        then:
            dog.class.isInstance(dog2)
            cat.class.isInstance(cat2)
            dog3.name == "Fred"
            dog3.barkVolume == 1.1d
            dog3.type == "dog"
            cat3.name == "Joe"
            cat3.likesCream
            cat3.lives == 9
            cat3.type == "cat"

        cleanup:
            context.close()
    }

    void "test @JsonTypeInfo(visible=false)"() {
        given:
            def context = buildContext("""
package test;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME,
  property = "type")
interface Animal {
    String getName();
}

@JsonTypeName("dog")
class Dog implements Animal {

    public double barkVolume;
    private String name;
    public String type;

    @Override
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
}

@JsonTypeName("cat")
class Cat implements Animal {
    public boolean likesCream;
    public String type;
    public int lives;
    private String name;
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
}
""")

        when:
            def dog = newInstance(context, 'test.Dog', [name:"Fred", barkVolume:1.1d])
            def cat = newInstance(context, 'test.Cat', [name:"Joe", likesCream:true, lives: 9])
            def dogJson = writeJson(jsonMapper, dog)
            def catJson = writeJson(jsonMapper, cat)

        then:
            dogJson == '{"type":"dog","name":"Fred","barkVolume":1.1}'
            catJson == '{"type":"cat","name":"Joe","likesCream":true,"lives":9}'

        when:
            def dog2 = jsonMapper.readValue(dogJson, argumentOf(context, 'test.Dog'))
            def cat2 = jsonMapper.readValue(catJson, argumentOf(context, 'test.Cat'))

        then:
            dog.class.isInstance(dog2)
            cat.class.isInstance(cat2)
            dog2.name == "Fred"
            dog2.barkVolume == 1.1d
            dog2.type == null
            cat2.name == "Joe"
            cat2.likesCream
            cat2.lives == 9
            cat2.type == null

        when:
            def dog3 = jsonMapper.readValue(dogJson, argumentOf(context, 'test.Animal'))
            def cat3 = jsonMapper.readValue(catJson, argumentOf(context, 'test.Animal'))

        then:
            dog.class.isInstance(dog2)
            cat.class.isInstance(cat2)
            dog3.name == "Fred"
            dog3.barkVolume == 1.1d
            dog3.type == null
            cat3.name == "Joe"
            cat3.likesCream
            cat3.lives == 9
            cat3.type == null

        cleanup:
            context.close()
    }

    void "test @JsonTypeInfo(visible=true) renamed property and @JsonIgnoreProperties(allowSetters=true)2"() {

        when:

        def context = buildContext("""
package test;

import java.util.Optional;

import jakarta.annotation.Generated;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.serde.jackson.data.ColorEnum;
import java.math.BigDecimal;
import jakarta.validation.constraints.NotNull;

/**
 * Animal
 */
@Serdeable
@JsonPropertyOrder({
    Animal.JSON_PROPERTY_PROPERTY_CLASS,
    Animal.JSON_PROPERTY_COLOR
})
@JsonIgnoreProperties(
        value = "class", // ignore manually set class, it will be automatically generated by Jackson during serialization
        allowSetters = true // allows the class to be set during deserialization
)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "class", visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = Bird.class, name = "ave"),
    @JsonSubTypes.Type(value = Mammal.class, name = "mammalia"),
    @JsonSubTypes.Type(value = Reptile.class, name = "reptilia")
})
@Introspected
class Animal {

    public static final String JSON_PROPERTY_PROPERTY_CLASS = "class";
    public static final String JSON_PROPERTY_COLOR = "color";

    @JsonProperty(JSON_PROPERTY_PROPERTY_CLASS)
    @JsonInclude(JsonInclude.Include.USE_DEFAULTS)
    protected String propertyClass;

    @JsonProperty(JSON_PROPERTY_COLOR)
    @JsonInclude(JsonInclude.Include.USE_DEFAULTS)
    private ColorEnum color;

    /**
     * @return the propertyClass property value
     */
    public String getPropertyClass() {
        return propertyClass;
    }

    /**
     * @return the propertyClass property value wrapped in an optional
     */
    @JsonIgnore
    public Optional<String> getPropertyClassOptional() {
        return Optional.ofNullable(propertyClass);
    }

    /**
     * Set the propertyClass property value
     */
    public void setPropertyClass(String propertyClass) {
        this.propertyClass = propertyClass;
    }

    /**
     * Set propertyClass in a chainable fashion.
     *
     * @return The same instance of Animal for chaining.
     */
    public Animal propertyClass(String propertyClass) {
        this.propertyClass = propertyClass;
        return this;
    }

    /**
     * @return the color property value
     */
    public ColorEnum getColor() {
        return color;
    }

    /**
     * @return the color property value wrapped in an optional
     */
    @JsonIgnore
    public Optional<ColorEnum> getColorOptional() {
        return Optional.ofNullable(color);
    }

    /**
     * Set the color property value
     */
    public void setColor(ColorEnum color) {
        this.color = color;
    }

    /**
     * Set color in a chainable fashion.
     *
     * @return The same instance of Animal for chaining.
     */
    public Animal color(ColorEnum color) {
        this.color = color;
        return this;
    }
}

/**
 * Bird
 */
@Serdeable
@JsonPropertyOrder({
    Bird.JSON_PROPERTY_NUM_WINGS,
    Bird.JSON_PROPERTY_BEAK_LENGTH,
    Bird.JSON_PROPERTY_FEATHER_DESCRIPTION
})
@Generated("io.micronaut.openapi.generator.JavaMicronautServerCodegen")
@Introspected
class Bird extends Animal {

    public static final String JSON_PROPERTY_NUM_WINGS = "numWings";
    public static final String JSON_PROPERTY_BEAK_LENGTH = "beakLength";
    public static final String JSON_PROPERTY_FEATHER_DESCRIPTION = "featherDescription";

    @JsonProperty(JSON_PROPERTY_NUM_WINGS)
    @JsonInclude(JsonInclude.Include.USE_DEFAULTS)
    private Integer numWings;

    @JsonProperty(JSON_PROPERTY_BEAK_LENGTH)
    @JsonInclude(JsonInclude.Include.USE_DEFAULTS)
    private BigDecimal beakLength;

    @JsonProperty(JSON_PROPERTY_FEATHER_DESCRIPTION)
    @JsonInclude(JsonInclude.Include.USE_DEFAULTS)
    private String featherDescription;

    /**
     * @return the numWings property value
     */
    public Integer getNumWings() {
        return numWings;
    }

    /**
     * @return the numWings property value wrapped in an optional
     */
    @JsonIgnore
    public Optional<Integer> getNumWingsOptional() {
        return Optional.ofNullable(numWings);
    }

    /**
     * Set the numWings property value
     */
    public void setNumWings(Integer numWings) {
        this.numWings = numWings;
    }

    /**
     * Set numWings in a chainable fashion.
     *
     * @return The same instance of Bird for chaining.
     */
    public Bird numWings(Integer numWings) {
        this.numWings = numWings;
        return this;
    }

    /**
     * @return the beakLength property value
     */
    public BigDecimal getBeakLength() {
        return beakLength;
    }

    /**
     * @return the beakLength property value wrapped in an optional
     */
    @JsonIgnore
    public Optional<BigDecimal> getBeakLengthOptional() {
        return Optional.ofNullable(beakLength);
    }

    /**
     * Set the beakLength property value
     */
    public void setBeakLength(BigDecimal beakLength) {
        this.beakLength = beakLength;
    }

    /**
     * Set beakLength in a chainable fashion.
     *
     * @return The same instance of Bird for chaining.
     */
    public Bird beakLength(BigDecimal beakLength) {
        this.beakLength = beakLength;
        return this;
    }

    /**
     * @return the featherDescription property value
     */
    public String getFeatherDescription() {
        return featherDescription;
    }

    /**
     * @return the featherDescription property value wrapped in an optional
     */
    @JsonIgnore
    public Optional<String> getFeatherDescriptionOptional() {
        return Optional.ofNullable(featherDescription);
    }

    /**
     * Set the featherDescription property value
     */
    public void setFeatherDescription(String featherDescription) {
        this.featherDescription = featherDescription;
    }

    /**
     * Set featherDescription in a chainable fashion.
     *
     * @return The same instance of Bird for chaining.
     */
    public Bird featherDescription(String featherDescription) {
        this.featherDescription = featherDescription;
        return this;
    }

    @Override
    public Bird propertyClass(String propertyClass) {
        super.setPropertyClass(propertyClass);
        return this;
    }

    @Override
    public Bird color(ColorEnum color) {
        super.setColor(color);
        return this;
    }
}

/**
 * Mammal
 */
@Serdeable
@JsonPropertyOrder({
    Mammal.JSON_PROPERTY_WEIGHT,
    Mammal.JSON_PROPERTY_DESCRIPTION
})
@Generated("io.micronaut.openapi.generator.JavaMicronautServerCodegen")
@Introspected
class Mammal extends Animal {

    public static final String JSON_PROPERTY_WEIGHT = "weight";
    public static final String JSON_PROPERTY_DESCRIPTION = "description";

    @NotNull
    @JsonProperty(JSON_PROPERTY_WEIGHT)
    private Float weight;

    @NotNull
    @JsonProperty(JSON_PROPERTY_DESCRIPTION)
    private String description;

    public Mammal() {
    }

    public Mammal(Float weight, String description) {
        this.weight = weight;
        this.description = description;
    }

    /**
     * @return the weight property value
     */
    public Float getWeight() {
        return weight;
    }

    /**
     * Set the weight property value
     */
    public void setWeight(Float weight) {
        this.weight = weight;
    }

    /**
     * Set weight in a chainable fashion.
     *
     * @return The same instance of Mammal for chaining.
     */
    public Mammal weight(Float weight) {
        this.weight = weight;
        return this;
    }

    /**
     * @return the description property value
     */
    public String getDescription() {
        return description;
    }

    /**
     * Set the description property value
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Set description in a chainable fashion.
     *
     * @return The same instance of Mammal for chaining.
     */
    public Mammal description(String description) {
        this.description = description;
        return this;
    }

    @Override
    public Mammal propertyClass(String propertyClass) {
        super.setPropertyClass(propertyClass);
        return this;
    }

    @Override
    public Mammal color(ColorEnum color) {
        super.setColor(color);
        return this;
    }
}

/**
 * Reptile
 */
@Serdeable
@JsonPropertyOrder({
    Reptile.JSON_PROPERTY_NUM_LEGS,
    Reptile.JSON_PROPERTY_FANGS,
    Reptile.JSON_PROPERTY_FANG_DESCRIPTION
})
@Generated("io.micronaut.openapi.generator.JavaMicronautServerCodegen")
@Introspected
class Reptile extends Animal {

    public static final String JSON_PROPERTY_NUM_LEGS = "numLegs";
    public static final String JSON_PROPERTY_FANGS = "fangs";
    public static final String JSON_PROPERTY_FANG_DESCRIPTION = "fangDescription";

    @NotNull
    @JsonProperty(JSON_PROPERTY_NUM_LEGS)
    private Integer numLegs;

    @NotNull
    @JsonProperty(JSON_PROPERTY_FANGS)
    private Boolean fangs;

    @JsonProperty(JSON_PROPERTY_FANG_DESCRIPTION)
    @JsonInclude(JsonInclude.Include.USE_DEFAULTS)
    private String fangDescription;

    public Reptile() {
    }

    public Reptile(Integer numLegs, Boolean fangs) {
        this.numLegs = numLegs;
        this.fangs = fangs;
    }

    /**
     * @return the numLegs property value
     */
    public Integer getNumLegs() {
        return numLegs;
    }

    /**
     * Set the numLegs property value
     */
    public void setNumLegs(Integer numLegs) {
        this.numLegs = numLegs;
    }

    /**
     * Set numLegs in a chainable fashion.
     *
     * @return The same instance of Reptile for chaining.
     */
    public Reptile numLegs(Integer numLegs) {
        this.numLegs = numLegs;
        return this;
    }

    /**
     * @return the fangs property value
     */
    public Boolean getFangs() {
        return fangs;
    }

    /**
     * Set the fangs property value
     */
    public void setFangs(Boolean fangs) {
        this.fangs = fangs;
    }

    /**
     * Set fangs in a chainable fashion.
     *
     * @return The same instance of Reptile for chaining.
     */
    public Reptile fangs(Boolean fangs) {
        this.fangs = fangs;
        return this;
    }

    /**
     * @return the fangDescription property value
     */
    public String getFangDescription() {
        return fangDescription;
    }

    /**
     * @return the fangDescription property value wrapped in an optional
     */
    @JsonIgnore
    public Optional<String> getFangDescriptionOptional() {
        return Optional.ofNullable(fangDescription);
    }

    /**
     * Set the fangDescription property value
     */
    public void setFangDescription(String fangDescription) {
        this.fangDescription = fangDescription;
    }

    /**
     * Set fangDescription in a chainable fashion.
     *
     * @return The same instance of Reptile for chaining.
     */
    public Reptile fangDescription(String fangDescription) {
        this.fangDescription = fangDescription;
        return this;
    }

    @Override
    public Reptile propertyClass(String propertyClass) {
        super.setPropertyClass(propertyClass);
        return this;
    }

    @Override
    public Reptile color(ColorEnum color) {
        super.setColor(color);
        return this;
    }
}
        """)

        def bird = newInstance(context, 'test.Bird', [numWings: 2, beakLength: BigDecimal.valueOf(12, 1), featherDescription: "Large blue and white feathers", color: ColorEnum.BLUE])
        def mammal = newInstance(context, 'test.Mammal', [weight: 20.5f, description: "A typical Canadian beaver", color: ColorEnum.BLUE])

        def reptile = newInstance(context, 'test.Reptile', [numLegs: 0, fangs: true, fangDescription: "A pair of venomous fangs", color: ColorEnum.BLUE])

        def birdJson = writeJson(jsonMapper, bird)
        def mammalJson = writeJson(jsonMapper, mammal)
        def reptileJson = writeJson(jsonMapper, reptile)

        then:
        birdJson == '{"class":"ave","numWings":2,"beakLength":1.2,"featherDescription":"Large blue and white feathers","color":"blue"}'
        mammalJson == '{"class":"mammalia","weight":20.5,"description":"A typical Canadian beaver","color":"blue"}'
        reptileJson == '{"class":"reptilia","numLegs":0,"fangs":true,"fangDescription":"A pair of venomous fangs","color":"blue"}'

        when:
        def bird2 = jsonMapper.readValue(birdJson, argumentOf(context, 'test.Bird'))
        def mammal2 = jsonMapper.readValue(mammalJson, argumentOf(context, 'test.Mammal'))
        def reptile2 = jsonMapper.readValue(reptileJson, argumentOf(context, 'test.Reptile'))

        then:
        bird.class.isInstance(bird2)
        mammal.class.isInstance(mammal2)
        reptile.class.isInstance(reptile2)
//        dog2.name == "Fred"
//        dog2.barkVolume == 1.1d
//        dog2.objType == "dog"
//        cat2.name == "Joe"
//        cat2.likesCream
//        cat2.lives == 9
//        cat2.objType == "cat"

//        when:
//        def dog3 = jsonMapper.readValue(dogJson, argumentOf(context, 'test.Animal'))
//        def cat3 = jsonMapper.readValue(catJson, argumentOf(context, 'test.Animal'))
//
//        then:
//        dog.class.isInstance(dog2)
//        cat.class.isInstance(cat2)
//        dog3.name == "Fred"
//        dog3.barkVolume == 1.1d
//        dog3.objType == "dog"
//        cat3.name == "Joe"
//        cat3.likesCream
//        cat3.lives == 9
//        cat3.objType == "cat"

//        cleanup:
//        context.close()
    }

    void "test JsonIgnoreProperties on supertype"() {
        given:
            def context = buildContext("""
package test;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME,
  property = "type",
  defaultImpl = Dog.class)
@JsonIgnoreProperties("name")
interface Animal {
    String getName();
}

@JsonTypeName("dog")
class Dog implements Animal {
    public double barkVolume;
    private String name;
    @Override public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
}

@JsonTypeName("cat")
class Cat implements Animal {
    public boolean likesCream;
    public int lives;
    private String name;
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
}
""")

        when:
            def dog = newInstance(context, 'test.Dog', [name:"Fred", barkVolume:1.1d])
            def cat = newInstance(context, 'test.Cat', [name:"Joe", likesCream:true, lives: 9])
            def dogJson = writeJson(jsonMapper, dog)
            def catJson = writeJson(jsonMapper, cat)

        then:
            dogJson == '{"type":"dog","barkVolume":1.1}'
            catJson == '{"type":"cat","likesCream":true,"lives":9}'

        when:
            def dogClass = dog.getClass()
            def catClass = cat.getClass()
            def dogBean = jsonMapper.readValue('{"name":"Fred","barkVolume":1.1}', argumentOf(context, 'test.Animal'))
            def catBean = jsonMapper.readValue(catJson, argumentOf(context, 'test.Animal'))

        then:
            catClass.isInstance(catBean)
            dogClass.isInstance(dogBean)
            dogBean.name == null
            dogBean.barkVolume == 1.1d
            catBean.name == null
            catBean.likesCream
            catBean.lives == 9

        cleanup:
            context.close()
    }

    void "test scenario"() {
        given:
            def context = buildContext("""
package test;


import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@Serdeable
@JsonPropertyOrder({
        "author"
})
@Introspected
class BasicBookInfo extends BookInfo {

    private String author;

    public BasicBookInfo() {
    }

    public BasicBookInfo(String author, String name, TypeEnum type) {
        super(name, type);
        this.author = author;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }
}

@Serdeable
@JsonPropertyOrder({
        "name",
        "type"
})
@JsonIgnoreProperties(
  value = "type",
  allowSetters = true
)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = BasicBookInfo.class, name = "BASIC")
})
@Introspected
class BookInfo {

    private String name;

    protected TypeEnum type;

    public BookInfo() {
    }

    public BookInfo(String name, TypeEnum type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TypeEnum getType() {
        return type;
    }

    public void setType(TypeEnum type) {
        this.type = type;
    }

    @Serdeable
    public enum TypeEnum {
        BASIC("BASIC"),
        DETAILED("DETAILED");

        private final String value;

        TypeEnum(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }

        private final static Map<String, TypeEnum> VALUE_MAPPING = Arrays.stream(values())
            .collect(Collectors.toMap(v -> v.getValue(), v -> v));

        @JsonCreator
        public static TypeEnum fromValue(String value) {
            if (!VALUE_MAPPING.containsKey(value)) {
                throw new IllegalArgumentException("Unexpected value '" + value + "'");
            }
            return VALUE_MAPPING.get(value);
        }
    }
}
""")

        when:

            def testModel = newInstance(context, 'test.BasicBookInfo', [
                    author: "Michael Ende",
                    name: "The Neverending Story",
                    type: context.classLoader.loadClass('test.BookInfo$TypeEnum').enumConstants[0]]
            )
            def json = writeJson(jsonMapper, testModel)

        then:
            json == '{"type":"BASIC","author":"Michael Ende","name":"The Neverending Story"}'

        when:
            def bean = jsonMapper.readValue(json, context.classLoader.loadClass('test.BookInfo'))
        then:
            bean.author == "Michael Ende"
            bean.name == "The Neverending Story"
            bean.type.toString() == "BASIC"

        when:
            bean = jsonMapper.readValue(json, context.classLoader.loadClass('test.BasicBookInfo'))
        then:
            bean.author == "Michael Ende"
            bean.name == "The Neverending Story"
            bean.type.toString() == "BASIC"

        cleanup:
            context.close()
    }
}
