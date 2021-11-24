package io.micronaut.serde.jackson.annotation

import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.micronaut.serde.jackson.JsonCompileSpec
import spock.lang.Ignore
import spock.lang.Unroll

class JsonTypeInfoSpec extends JsonCompileSpec {

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
        dogJson == '{"type":"dog","barkVolume":1.1,"name":"Fred"}'
        catJson == '{"type":"cat","likesCream":true,"lives":9,"name":"Joe"}'

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
        dogJson == '{"dog":{"barkVolume":1.1,"name":"Fred"}}'
        catJson == '{"cat":{"likesCream":true,"lives":9,"name":"Joe"}}'

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
        dogJson == '{"@class":"subtypes.Dog","barkVolume":1.1,"name":"Fred"}'
        catJson == '{"@class":"subtypes.Cat","likesCream":true,"lives":9,"name":"Joe"}'

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
        dogBean = jsonMapper.readValue('{"barkVolume":1.1,"name":"Fred","@class":"subtypes.Dog"}', argumentOf(context, 'subtypes.Animal'))
        catBean = jsonMapper.readValue('{"likesCream":true,"lives":9,"name":"Joe","@class":"subtypes.Cat"}', argumentOf(context, 'subtypes.Animal'))

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
        dogJson == '{"type":"dog","barkVolume":1.1,"name":"Fred"}'
        catJson == '{"type":"cat","likesCream":true,"lives":9,"name":"Joe"}'

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
}
