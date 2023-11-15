package io.micronaut.serde.jackson


import io.micronaut.core.type.Argument

abstract class JsonTypeInfoSpec extends JsonCompileSpec {

    protected abstract boolean jacksonCustomOrder()

    void "test default implementation - with @JsonDeserialize(as)"() {
        given:
        def context = buildContext("""
package defaultimpl;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.micronaut.context.annotation.DefaultImplementation;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@JsonDeserialize(as = Dog.class)
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
@JsonSubTypes({
  @JsonSubTypes.Type(Dog.class),
  @JsonSubTypes.Type(Cat.class)
})
interface Animal {
    String getName();
}

@JsonTypeName("dog")
class Dog implements Animal {

    private String name;
    public double barkVolume;

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

    private String name;
    public boolean likesCream;
    public int lives;

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

@Serdeable
class Cat implements Animal {
    private String name;
    private int lives;
    private boolean likesCream;
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
        def cl = Thread.currentThread().getContextClassLoader()
        Thread.currentThread().setContextClassLoader(context.classLoader)
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
        Thread.currentThread().setContextClassLoader(cl)
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
@JsonSubTypes({
  @JsonSubTypes.Type(Dog.class),
  @JsonSubTypes.Type(Cat.class)
})
interface Animal {
    String getName();
}

@JsonTypeName("dog")
class Dog implements Animal {
    private String name;
    public double barkVolume;
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
    private String name;
    public boolean likesCream;
    public int lives;

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
@JsonSubTypes({
  @JsonSubTypes.Type(Dog.class),
  @JsonSubTypes.Type(Cat.class)
})
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
@JsonSubTypes({
  @JsonSubTypes.Type(Dog.class),
  @JsonSubTypes.Type(Cat.class)
})
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
@JsonSubTypes({
  @JsonSubTypes.Type(Dog.class),
  @JsonSubTypes.Type(Cat.class)
})
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
        def cl = Thread.currentThread().getContextClassLoader()
        Thread.currentThread().setContextClassLoader(context.classLoader)
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
        Thread.currentThread().setContextClassLoader(cl)
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
        if (jacksonCustomOrder()) {
            assert dogJson == '{"type":"dog","barkVolume":1.1,"name":"Fred"}'
            assert catJson == '{"type":"cat","name":"Joe","lives":9,"likesCream":true}'
        } else {
            assert dogJson == '{"type":"dog","name":"Fred","barkVolume":1.1}'
            assert catJson == '{"type":"cat","name":"Joe","likesCream":true,"lives":9}'
        }

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
        dogBean = jsonMapper.readValue(dogJson, argumentOf(context, 'subtypes.Animal'))
        catBean = jsonMapper.readValue(catJson, argumentOf(context, 'subtypes.Animal'))

        then:
        catClass.isInstance(catBean)
        dogClass.isInstance(dogBean)
        dogBean.name == "Fred"
        dogBean.barkVolume == 1.1d
        catBean.name == "Joe"
        catBean.likesCream
        catBean.lives == 9

        when:"the buffer is used again"
        dogBean = jsonMapper.readValue(dogJson, argumentOf(context, 'subtypes.Animal'))
        catBean = jsonMapper.readValue(catJson, argumentOf(context, 'subtypes.Animal'))

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
@JsonSubTypes({
  @JsonSubTypes.Type(Dog.class),
  @JsonSubTypes.Type(Cat.class)
})
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
        if (jacksonCustomOrder()) {
            assert dogJson == '{"type":"dog","barkVolume":1.1,"friend":{"type":"cat","name":"Joe","lives":9,"likesCream":true},"name":"Fred"}'
            assert catJson == '{"type":"cat","name":"Joe","lives":9,"likesCream":true}'
        } else {
            assert dogJson == '{"type":"dog","name":"Fred","barkVolume":1.1,"friend":{"type":"cat","name":"Joe","likesCream":true,"lives":9}}'
            assert catJson == '{"type":"cat","name":"Joe","likesCream":true,"lives":9}'
        }

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
@JsonSubTypes({
  @JsonSubTypes.Type(Dog.class),
  @JsonSubTypes.Type(Cat.class)
})
interface Animal {
    String getName();
}

@JsonTypeName("dog")
class Dog implements Animal {

    private String name;
    public double barkVolume;
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
    private String name;
    public boolean likesCream;
    public String type;
    public int lives;
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
@JsonSubTypes({
  @JsonSubTypes.Type(Dog.class),
  @JsonSubTypes.Type(Cat.class)
})
interface Animal {
    String getName();
}

@JsonTypeName("dog")
class Dog implements Animal {

    private String name;
    public double barkVolume;
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
    private String name;
    public boolean likesCream;
    public String type;
    public int lives;
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

    void "test @JsonTypeInfo(visible=true) renamed property"() {
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
@JsonSubTypes({
  @JsonSubTypes.Type(Dog.class),
  @JsonSubTypes.Type(Cat.class)
})
abstract class Animal {

    @JsonProperty("type")
    private String objType;

    public abstract String getName();

    public void setObjType(String objType) {
        this.objType = objType;
    }

    public String getObjType() {
        return objType;
    }
}

@JsonTypeName("dog")
class Dog extends Animal {

    private String name;
    public double barkVolume;

    @Override
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
}

@JsonTypeName("cat")
class Cat extends Animal {
    private String name;
    public boolean likesCream;
    public int lives;
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
            dog2.objType == "dog"
            cat2.name == "Joe"
            cat2.likesCream
            cat2.lives == 9
            cat2.objType == "cat"

        when:
            def dog3 = jsonMapper.readValue(dogJson, argumentOf(context, 'test.Animal'))
            def cat3 = jsonMapper.readValue(catJson, argumentOf(context, 'test.Animal'))

        then:
            dog.class.isInstance(dog2)
            cat.class.isInstance(cat2)
            dog3.name == "Fred"
            dog3.barkVolume == 1.1d
            dog3.objType == "dog"
            cat3.name == "Joe"
            cat3.likesCream
            cat3.lives == 9
            cat3.objType == "cat"

        cleanup:
            context.close()
    }

    void "test @JsonTypeInfo(visible=true) renamed property and @JsonIgnoreProperties(allowSetters=true)"() {
        given:
            def context = buildContext("""
package test;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import java.util.Optional;

@JsonTypeInfo(
   use = JsonTypeInfo.Id.NAME,
   property = "type",
   visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(Dog.class),
  @JsonSubTypes.Type(Cat.class)
})
@JsonIgnoreProperties(
    value = "type",
    allowSetters = true
)
abstract class Animal {

    @JsonProperty("type")
    private String objType;

    public abstract String getName();

    public void setObjType(String objType) {
        this.objType = objType;
    }

    public String getObjType() {
        return objType;
    }

    @JsonIgnore
    public Optional<String> getObjTypeOptional() {
        return Optional.ofNullable(objType);
    }

}

@JsonTypeName("dog")
class Dog extends Animal {

    private String name;
    public double barkVolume;

    @Override
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
}

@JsonTypeName("cat")
class Cat extends Animal {
    private String name;
    public boolean likesCream;
    public int lives;
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
            dog2.objType == "dog"
            cat2.name == "Joe"
            cat2.likesCream
            cat2.lives == 9
            cat2.objType == "cat"

        when:
            def dog3 = jsonMapper.readValue(dogJson, argumentOf(context, 'test.Animal'))
            def cat3 = jsonMapper.readValue(catJson, argumentOf(context, 'test.Animal'))

        then:
            dog.class.isInstance(dog2)
            cat.class.isInstance(cat2)
            dog3.name == "Fred"
            dog3.barkVolume == 1.1d
            dog3.objType == "dog"
            cat3.name == "Joe"
            cat3.likesCream
            cat3.lives == 9
            cat3.objType == "cat"

        cleanup:
            context.close()
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
@JsonSubTypes({
  @JsonSubTypes.Type(Dog.class),
  @JsonSubTypes.Type(Cat.class)
})
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
