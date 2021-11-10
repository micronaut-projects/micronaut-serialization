package io.micronaut.serde.bson

import io.micronaut.core.type.Argument
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.bson.BsonDocument
import org.bson.BsonObjectId
import org.bson.types.ObjectId
import spock.lang.PendingFeature
import spock.lang.Specification

import java.nio.charset.StandardCharsets

@MicronautTest
class BsonMappingSpec extends Specification implements BsonJsonSpec, BsonBinarySpec {

    @Inject
    BsonBinaryMapper bsonBinaryMapper

    @Inject
    BsonJsonMapper bsonJsonMapper

    def "validate mapping"() {
        given:
            def expectedJson = """{"_t": "Person", "_id": "12345", "firstName": "John", "surname": "Smith", "addr": {"_t": "Address", "address": "The home", "street": "Downstreet", "town": "Paris", "postcode": "123456"}}"""
            def person = new Person("12345", "John", "Smith", "p4sw0rd", new Address("The home", "Downstreet", "Paris", "123456"))
        expect:
            asBsonJsonString(person) == expectedJson
            encodeAsBinaryDecodeJson(person) == expectedJson
            encodeAsBinaryDecodeAsObject(person) == {
                person.password = null
                person
            }.call()
    }

    def "validate mapping representation"() {
        given:
            def product = new Product(name: "Abc", serialNumber: new ObjectId().toHexString())
        when:
            def data = bsonBinaryMapper.writeValueAsBytes(product)
            def bsonDocument = bsonBinaryMapper.readValue(data, Argument.of(BsonDocument))
        then:
            bsonDocument.get("serialNumber") instanceof BsonObjectId
    }

    def "validate mapping inheritance"() {
        given:
            def dog = new Dog(id: new ObjectId(), name: "Bark", breed: "Unknown")
            def cat = new Cat(id: new ObjectId(), name: "Meow", kitten: true)
        when:
            def dogData = bsonBinaryMapper.writeValueAsBytes(dog)
            def catData = bsonBinaryMapper.writeValueAsBytes(cat)
            def newDog = bsonBinaryMapper.readValue(dogData, Argument.of(AbstractPet)) as Dog
            def newCat = bsonBinaryMapper.readValue(catData, Argument.of(AbstractPet)) as Cat
        then:
            newDog instanceof Dog
            newDog.id == dog.id
            newDog.name == dog.name
            newDog.breed == dog.breed
            newCat instanceof Cat
            newCat.id == cat.id
            newCat.name == cat.name
            newCat.kitten == cat.kitten
    }

    def "validate mapping inheritance of field"() {
        given:
            def dog = new Dog(id: new ObjectId(), name: "Bark", breed: "Unknown")
            def cat = new Cat(id: new ObjectId(), name: "Meow", kitten: true)
            def pets = new Pets(pets: [dog, cat])
        when:
            def data = bsonBinaryMapper.writeValueAsBytes(pets)
            def newPets = bsonBinaryMapper.readValue(data, Argument.of(Pets))
        then:
            newPets.pets[0] instanceof Dog
            newPets.pets[1] instanceof Cat
    }

    @PendingFeature(reason = "Should work when 'decodeBuffer' is implemented")
    def "validate mapping inheritance type order"() {
        given:
            def dogJson = """{"breed": "Unknown", "_id": {"\$oid": "618b890200bbd4063ab74213"}, "name": "Bark", "_t": "Dog"}"""
            def catJson = """{"kitten": true, "_id": {"\$oid": "618b890300bbd4063ab74214"}, "name": "Meow", "_t": "Cat"}"""
        when:
            def newDog = bsonJsonMapper.readValue(dogJson.getBytes(StandardCharsets.UTF_8), Argument.of(AbstractPet)) as Dog
            def newCat = bsonJsonMapper.readValue(catJson.getBytes(StandardCharsets.UTF_8), Argument.of(AbstractPet)) as Cat
        then:
            newDog instanceof Dog
            newDog.id
            newDog.name == "Bark"
            newDog.breed == "Unknown"
            newCat instanceof Cat
            newCat.id
            newCat.name == "Meow"
            newCat.kitten
    }

}
