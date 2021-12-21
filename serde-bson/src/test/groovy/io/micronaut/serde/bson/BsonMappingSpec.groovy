package io.micronaut.serde.bson

import io.micronaut.core.type.Argument
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.bson.BsonBinaryReader
import org.bson.BsonDocument
import org.bson.BsonObjectId
import org.bson.BsonType
import org.bson.BsonValue
import org.bson.types.ObjectId
import spock.lang.Specification

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

@MicronautTest
class BsonMappingSpec extends Specification implements BsonJsonSpec, BsonBinarySpec {

    @Inject
    BsonBinaryMapper bsonBinaryMapper

    @Inject
    BsonJsonMapper bsonJsonMapper

    @Inject
    SerdeRegistry serdeRegistry

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

    def "validate mapping with constructor"() {
        given:
            def expectedJson = """{"_t": "Person2", "personId": "12345", "firstName": "John", "lastName": "Smith", "address": {"_t": "Address", "address": "The home", "street": "Downstreet", "town": "Paris", "postcode": "123456"}}"""
            def person = new Person2("12345", "John", "Smith", new Address("The home", "Downstreet", "Paris", "123456"))
        expect:
            asBsonJsonString(person) == expectedJson
            encodeAsBinaryDecodeJson(person) == expectedJson
            encodeAsBinaryDecodeAsObject(person) == person
    }

    def "validate mapping with constructor and annotation hierarchy"() {
        given:
            def expectedWriteJson = """{"_t": "Person3", "_id": "12345", "firstName": "John", "surname": "Smith", "address": {"_t": "Address", "address": "The home", "street": "Downstreet", "town": "Paris", "postcode": "123456"}}"""
            def readJson = """{"_t": "Person3", "personId": "12345", "firstName": "John", "lastName": "Smith", "address": {"_t": "Address", "address": "The home", "street": "Downstreet", "town": "Paris", "postcode": "123456"}}"""
            def person = new Person3("12345", "John", "Smith", new Address("The home", "Downstreet", "Paris", "123456"))
        expect:
            asBsonJsonString(person) == expectedWriteJson
            encodeAsBinaryDecodeJson(person) == expectedWriteJson
            bsonJsonMapper.readValue(readJson.getBytes(StandardCharsets.UTF_8), Argument.of(Person3)) == person
    }

    def "validate parser starting at array nested level"() {
        given:
            def person = new Person2("12345", "John", "Smith", new Address("The home", "Downstreet", "Paris", "123456"))
        when:
            def bytes = bsonBinaryMapper.writeValueAsBytes(new Wrapper1(persons: [person, person, person]))
            def reader = new BsonBinaryReader(ByteBuffer.wrap(bytes))
            reader.readStartDocument()
            reader.readName()
            reader.readStartArray()

            List<Person2> list = new ArrayList<BsonValue>()
            while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
                list.add(
                        serdeRegistry.findDeserializer(Person2)
                                .deserialize(new BsonReaderDecoder(reader), serdeRegistry.newDecoderContext(Person2), Argument.of(Person2))
                )
            }

            reader.readEndArray()
            reader.readEndDocument()
        then:
            list.size() == 3
    }

    def "validate parser starting at object nested level"() {
        given:
            def person = new Person2("12345", "John", "Smith", new Address("The home", "Downstreet", "Paris", "123456"))
        when:
            def bytes = bsonBinaryMapper.writeValueAsBytes(new Wrapper2(person: person))
            def reader = new BsonBinaryReader(ByteBuffer.wrap(bytes))
            reader.readStartDocument()
            reader.readName()
            def readPerson = serdeRegistry.findDeserializer(Person2)
                    .deserialize(new BsonReaderDecoder(reader), serdeRegistry.newDecoderContext(Person2), Argument.of(Person2))

            reader.readEndDocument()
        then:
            readPerson == person
    }

    def "validate convertor"() {
        given:
            def sale = new Sale1()
            sale.quantity = Quantity.valueOf(123)
        when:
            def bytes = bsonBinaryMapper.writeValueAsBytes(sale)
            def newSale = bsonBinaryMapper.readValue(bytes, Argument.of(Sale1))
        then:
            newSale.quantity.amount == 123
        when:
            def jsonBytes = bsonJsonMapper.writeValueAsBytes(sale)
            def str = new String(jsonBytes)
            def readSale = bsonJsonMapper.readValue(str, Argument.of(Sale1))
            def newSaleBson = bsonJsonMapper.readValue(str, Argument.of(BsonDocument))
        then:
            readSale.quantity.amount == 123
            newSaleBson.get('quantity').isInt32()
    }

    def "validate convertor for constructor attribute"() {
        given:
            def sale = new Sale2(Quantity.valueOf(123))
        when:
            def bytes = bsonBinaryMapper.writeValueAsBytes(sale)
            def newSale = bsonBinaryMapper.readValue(bytes, Argument.of(Sale2))
        then:
            newSale.quantity.amount == 123
        when:
            def jsonBytes = bsonJsonMapper.writeValueAsBytes(sale)
            def str = new String(jsonBytes)
            def readSale = bsonJsonMapper.readValue(str, Argument.of(Sale2))
            def newSaleBson = bsonJsonMapper.readValue(str, Argument.of(BsonDocument))
        then:
            readSale.quantity.amount == 123
            newSaleBson.get('quantity').isInt32()
    }

    def "validate convertor for constructor attribute 1"() {
        given:
            def sale = new Sale3(Quantity.valueOf(123))
        when:
            def bytes = bsonBinaryMapper.writeValueAsBytes(sale)
            def newSale = bsonBinaryMapper.readValue(bytes, Argument.of(Sale3))
        then:
            newSale.quantity.amount == 123
        when:
            def jsonBytes = bsonJsonMapper.writeValueAsBytes(sale)
            def str = new String(jsonBytes)
            def readSale = bsonJsonMapper.readValue(str, Argument.of(Sale3))
            def newSaleBson = bsonJsonMapper.readValue(str, Argument.of(BsonDocument))
        then:
            readSale.quantity.amount == 123
            newSaleBson.get('quantity').isInt32()
    }

    def "validate convertor for constructor attribute 2"() {
        given:
            def sale = new Sale4(Quantity.valueOf(123))
        when:
            def bytes = bsonBinaryMapper.writeValueAsBytes(sale)
            def newSale = bsonBinaryMapper.readValue(bytes, Argument.of(Sale4))
        then:
            newSale.quantity.amount == 123
        when:
            def jsonBytes = bsonJsonMapper.writeValueAsBytes(sale)
            def str = new String(jsonBytes)
            def readSale = bsonJsonMapper.readValue(str, Argument.of(Sale4))
            def newSaleBson = bsonJsonMapper.readValue(str, Argument.of(BsonDocument))
        then:
            readSale.quantity.amount == 123
            newSaleBson.get('quantity').isInt32()
    }

    def "validate custom naming strategies"() {
        given:
            def e = new NamingStrategiesEntity(renameCompileTime: "val1", renameRunTime: "val2", notRenamedProperty: "test")
        when:
            def json = bsonJsonMapper.writeValueAsString(e);
        then:
            json == """{"rename-compile-time": "val1", "bar yes": "val2", "notRenamedProperty": "test", "_xyz": null, "rename-compile-time": "val1"}"""
        when:
            def value = bsonJsonMapper.readValue("""{"_xyz": "abc"}""", NamingStrategiesEntity)
        then:
            value.deserRenameRunTime == "abc"
    }

    @Serdeable
    static class Wrapper1 {
        List<Person2> persons
    }

    @Serdeable
    static class Wrapper2 {
        Person2 person
    }

}
