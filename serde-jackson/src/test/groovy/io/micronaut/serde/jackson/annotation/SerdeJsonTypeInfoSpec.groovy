package io.micronaut.serde.jackson.annotation

import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.serde.jackson.JsonTypeInfoSpec
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

     void "test default implementation - with @JsonDeserialize(as) X"() {
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
