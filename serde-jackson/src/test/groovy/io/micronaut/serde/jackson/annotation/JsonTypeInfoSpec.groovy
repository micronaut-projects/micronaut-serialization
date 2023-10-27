package io.micronaut.serde.jackson.annotation

import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.micronaut.core.type.Argument
import io.micronaut.serde.jackson.JsonCompileSpec
import spock.lang.Unroll

class JsonTypeInfoSpec extends JsonCompileSpec {
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
        use << [JsonTypeInfo.Id.DEDUCTION, JsonTypeInfo.Id.MINIMAL_CLASS, JsonTypeInfo.Id.CUSTOM]
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
}
