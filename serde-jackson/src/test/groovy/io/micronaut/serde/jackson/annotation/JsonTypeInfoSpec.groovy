package io.micronaut.serde.jackson.annotation

import io.micronaut.serde.jackson.JsonCompileSpec

class JsonTypeInfoSpec extends JsonCompileSpec {
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
    boolean likesCream;
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

        when:"the buffer is used"
        dogBean = jsonMapper.readValue('{"barkVolume":1.1,"name":"Fred","type":"dog"}', argumentOf(context, 'subtypes.Animal'))

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

        when:"the buffer is used"
        dogBean = jsonMapper.readValue('{"barkVolume":1.1,"name":"Fred","type":"dog"}', argumentOf(context, 'subtypes.Animal'))

        then:
        dogClass.isInstance(dogBean)
        dogBean.name == "Fred"
        dogBean.barkVolume == 1.1d

        cleanup:
        context.close()
    }
}
