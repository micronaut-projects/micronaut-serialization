package io.micronaut.serde.jackson.compiletime

import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.Deserializer
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.Serializer
import io.micronaut.serde.jackson.JsonCompileSpec

/**
 * Generated serdes are written for exactly one type. A lookup for a supertype of generated subtypes
 * must keep resolving to the runtime object serdes instead of failing on the ambiguity.
 */
class GeneratedSubtypeRegistrySpec extends JsonCompileSpec {

    void 'test generated subtype serdes are not selected for a supertype lookup'() {
        given:
        def context = buildContext('test.Animal', '''
package test;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Animal {
    private String petName;

    public String getPetName() {
        return petName;
    }

    public void setPetName(String petName) {
        this.petName = petName;
    }
}

@Serdeable
class Dog extends Animal {
    private String breedName;

    public String getBreedName() {
        return breedName;
    }

    public void setBreedName(String breedName) {
        this.breedName = breedName;
    }
}

@Serdeable
class Cat extends Animal {
    private boolean kitten;

    public boolean isKitten() {
        return kitten;
    }

    public void setKitten(boolean kitten) {
        this.kitten = kitten;
    }
}

@Serdeable
abstract class Vehicle {
    private String plate;

    public String getPlate() {
        return plate;
    }

    public void setPlate(String plate) {
        this.plate = plate;
    }
}

@Serdeable
class Car extends Vehicle {
}

@Serdeable
class Bike extends Vehicle {
}
''')
        def registry = context.getBean(SerdeRegistry)
        def mapper = context.getBean(JsonMapper)
        Class<?> animalType = context.classLoader.loadClass('test.Animal')
        Class<?> dogType = context.classLoader.loadClass('test.Dog')
        Class<?> vehicleType = context.classLoader.loadClass('test.Vehicle')
        Class<?> carType = context.classLoader.loadClass('test.Car')

        expect: 'every concrete type uses its own generated serdes'
        [animalType, dogType, carType].every { assertSelection(registry, it, true) }

        and: 'an abstract type is never generated'
        assertSelection(registry, vehicleType, false)

        when: 'a supertype with generated subtypes is deserialized'
        def dog = dogType.newInstance()
        dog.petName = 'Rex'
        dog.breedName = 'Collie'
        def animal = mapper.readValue('{"petName":"Rex"}', Argument.of(animalType))
        def vehicle = mapper.readValue('{"plate":"X"}', Argument.of(carType))

        then:
        animalType.isInstance(animal)
        animal.petName == 'Rex'
        carType.isInstance(vehicle)
        vehicle.plate == 'X'
        new String(mapper.writeValueAsBytes(Argument.of(animalType), dog), 'UTF-8') == '{"petName":"Rex"}'
        new String(mapper.writeValueAsBytes(Argument.of(dogType), dog), 'UTF-8') == '{"petName":"Rex","breedName":"Collie"}'

        cleanup:
        context.close()
    }

    void 'test types below a subtype declaration route to the runtime serdes'() {
        given:
        def context = buildContext('test.Pet', '''
package test;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Dog.class, name = "dog")
})
abstract class Pet {
    private String petName;

    public String getPetName() {
        return petName;
    }

    public void setPetName(String petName) {
        this.petName = petName;
    }
}

@Serdeable
class Dog extends Pet {
    private String breedName;

    public String getBreedName() {
        return breedName;
    }

    public void setBreedName(String breedName) {
        this.breedName = breedName;
    }
}
''')
        def registry = context.getBean(SerdeRegistry)
        def mapper = context.getBean(JsonMapper)
        Class<?> petType = context.classLoader.loadClass('test.Pet')
        Class<?> dogType = context.classLoader.loadClass('test.Dog')

        expect:
        assertSelection(registry, petType, false)
        assertSelection(registry, dogType, false)

        when:
        def dog = dogType.newInstance()
        dog.petName = 'Rex'
        dog.breedName = 'Collie'

        then: 'the subtype writes the discriminator whichever type it is serialized as'
        new String(mapper.writeValueAsBytes(Argument.of(petType), dog), 'UTF-8') == '{"type":"dog","petName":"Rex","breedName":"Collie"}'
        new String(mapper.writeValueAsBytes(Argument.of(dogType), dog), 'UTF-8') == '{"type":"dog","petName":"Rex","breedName":"Collie"}'
        dogType.isInstance(mapper.readValue('{"type":"dog","petName":"Rex","breedName":"Collie"}', Argument.of(petType)))

        cleanup:
        context.close()
    }

    private static boolean assertSelection(SerdeRegistry registry, Class<?> type, boolean generated) {
        Argument argument = Argument.of(type)
        Serializer serializer = registry.findSerializer(argument).createSpecific(registry.newEncoderContext(Object), argument)
        Deserializer deserializer = registry.findDeserializer(argument).createSpecific(registry.newDecoderContext(Object), argument)
        String prefix = "${type.package.name}.Serde${type.simpleName}"
        assert (serializer.class.name == prefix + 'Serializer') == generated
        assert (deserializer.class.name == prefix + 'Deserializer') == generated
        true
    }
}
