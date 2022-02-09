package io.micronaut.serde.bson


import io.micronaut.core.type.Argument
import io.micronaut.serde.Deserializer
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.serde.bson.custom.CodecBsonDecoder
import io.micronaut.serde.exceptions.SerdeException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.bson.BsonBinaryReader
import org.bson.BsonDocument
import org.bson.BsonObjectId
import org.bson.BsonReader
import org.bson.BsonType
import org.bson.BsonValue
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
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

    def "test read inputstream"() {
        when:
        def map = bsonJsonMapper.readValue('{"title": "The Stand", "pages": 454}'.bytes, Map)

        then:
        map.size() == 2

        when:
        map = bsonJsonMapper.readValue(new ByteArrayInputStream('{"title": "The Stand", "pages": 454}'.bytes), Map)

        then:
        map.size() == 2
    }

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
                def context = serdeRegistry.newDecoderContext(Person2)
                def argument = Argument.of(Person2)
                list.add(
                        serdeRegistry.findDeserializer(Person2)
                                .createSpecific(context, argument)
                                .deserialize(new BsonReaderDecoder(reader), context, argument)
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

            def context = serdeRegistry.newDecoderContext(Person2)
            def argument = Argument.of(Person2)
            def readPerson = serdeRegistry.findDeserializer(Person2).createSpecific(context, argument)
                    .deserialize(new BsonReaderDecoder(reader), context, argument)

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
            newSale.nullQuantity.amount == 123456
        when:
            def jsonBytes = bsonJsonMapper.writeValueAsBytes(sale)
            def str = new String(jsonBytes)
            def readSale = bsonJsonMapper.readValue(str, Argument.of(Sale1))
            def newSaleBson = bsonJsonMapper.readValue(str, Argument.of(BsonDocument))
        then:
            readSale.quantity.amount == 123
            readSale.nullQuantity.amount == 123456
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
            def json = bsonJsonMapper.writeValueAsString(e)
        then:
            json == """{"rename-compile-time": "val1", "bar yes": "val2", "notRenamedProperty": "test", "_xyz": null, "rename-compile-time": "val1"}"""
        when:
            def value = bsonJsonMapper.readValue("""{"_xyz": "abc"}""", NamingStrategiesEntity)
        then:
            value.deserRenameRunTime == "abc"
    }

    def "test bson document parsing"() {
        given:
            def addressArgument = Argument.of(Address)
            def context = serdeRegistry.newDecoderContext(null)
            def deserializer = serdeRegistry.findDeserializer(Address).createSpecific(context, addressArgument)
            def asCodec = new MappedCodec<Address>(serdeRegistry, deserializer, Address)
            def asDecoder = new CodecBsonDecoder<Address>(asCodec)

            def bsonDocumentAddress = BsonDocument.parse("""{"address": "The home", "street": "Downstreet", "town": "Paris", "postcode": "123456"}""")
        when:
            Address address = asDecoder.deserialize(new BsonReaderDecoder(bsonDocumentAddress.asBsonReader()), context, addressArgument)
        then:
            address.address == "The home"
    }

    def "test nested bson document parsing"() {
        given:
            def addressNestedArgument = Argument.of(NestedObjAddress)
            def context = serdeRegistry.newDecoderContext(null)
            def deserializer = serdeRegistry.findDeserializer(NestedObjAddress).createSpecific(context, addressNestedArgument)

            def bsonDocumentAddress = BsonDocument.parse("""{"firstName": "A", "address": {"address": "The home", "street": "Downstreet", "town": "Paris", "postcode": "123456"}, "lastName": "B"}""")
            def decoderContext = new DelegationDecoderContext(context) {

                @Override
                Deserializer findDeserializer(Argument type) {
                    if (type.getType() == Address.class) {
                        def addressDeserializer = serdeRegistry.findDeserializer(Address).createSpecific(context, Argument.of(Address))
                        def asCodec = new MappedCodec<Address>(serdeRegistry, addressDeserializer, Address)
                        return new CodecBsonDecoder<Address>(asCodec)
                    }
                    return super.findDeserializer(type)
                }
            }
        when:
            NestedObjAddress e = deserializer.deserialize(new BsonReaderDecoder(bsonDocumentAddress.asBsonReader()), decoderContext, Argument.of(NestedObjAddress))
        then:
            e.address.address == "The home"
            e.lastName == "B"
            e.firstName == "A"
    }

    def "test nested empty bson document parsing"() {
        given:
            def addressNestedArgument = Argument.of(NestedObjAddress)
            def context = serdeRegistry.newDecoderContext(null)
            def deserializer = serdeRegistry.findDeserializer(NestedObjAddress).createSpecific(context, addressNestedArgument)

            def bsonDocumentAddress = BsonDocument.parse("""{"address": {"address": "The home", "street": "Downstreet", "town": "Paris", "postcode": "123456"}}""")
            def decoderContext = new DelegationDecoderContext(context) {

                @Override
                Deserializer findDeserializer(Argument type) {
                    if (type.getType() == Address.class) {
                        def addressDeserializer = serdeRegistry.findDeserializer(Address).createSpecific(context, Argument.of(Address))
                        def asCodec = new MappedCodec<Address>(serdeRegistry, addressDeserializer, Address)
                        return new CodecBsonDecoder<Address>(asCodec)
                    }
                    return super.findDeserializer(type)
                }
            }
        when:
            NestedObjAddress e = deserializer.deserialize(new BsonReaderDecoder(bsonDocumentAddress.asBsonReader()), decoderContext, Argument.of(NestedObjAddress))
        then:
            e.address.address == "The home"
            e.lastName == null
            e.firstName == null
    }

    def "test nested bson document level2 parsing"() {
        given:
            def addressNestedArgument = Argument.of(NestedObj2Address)
            def context = serdeRegistry.newDecoderContext(null)
            def deserializer = serdeRegistry.findDeserializer(NestedObj2Address).createSpecific(context, addressNestedArgument)

            def bsonDocumentAddress = BsonDocument.parse("""{"address": {"firstName": "A", "address": {"address": "The home", "street": "Downstreet", "town": "Paris", "postcode": "123456"}, "lastName": "B"}}""")
            def decoderContext = new DelegationDecoderContext(context) {

                @Override
                Deserializer findDeserializer(Argument type) {
                    if (type.getType() == Address.class) {
                        def addressDeserializer = serdeRegistry.findDeserializer(Address).createSpecific(context, Argument.of(Address))
                        def asCodec = new MappedCodec<Address>(serdeRegistry, addressDeserializer, Address)
                        return new CodecBsonDecoder<Address>(asCodec)
                    }
                    return super.findDeserializer(type)
                }
            }
        when:
            NestedObj2Address e = deserializer.deserialize(new BsonReaderDecoder(bsonDocumentAddress.asBsonReader()), decoderContext, Argument.of(NestedObj2Address))
        then:
            e.address.address.address == "The home"
            e.address.lastName == "B"
            e.address.firstName == "A"
    }

    def "test nested array bson document parsing"() {
        given:
            def addressNestedArgument = Argument.of(NestedArrayAddress)
            def context = serdeRegistry.newDecoderContext(null)
            def deserializer = serdeRegistry.findDeserializer(NestedArrayAddress).createSpecific(context, addressNestedArgument)

            def bsonDocumentAddress = BsonDocument.parse("""{"firstName": "A", "addresses": [{"address": "The home", "street": "Downstreet", "town": "Paris", "postcode": "123456"}], "lastName": "B"}""")
            def decoderContext = new DelegationDecoderContext(context) {

                @Override
                Deserializer findDeserializer(Argument type) {
                    if (type.getType() == Address.class) {
                        def addressDeserializer = serdeRegistry.findDeserializer(Address).createSpecific(context, Argument.of(Address))
                        def asCodec = new MappedCodec<Address>(serdeRegistry, addressDeserializer, Address)
                        return new CodecBsonDecoder<Address>(asCodec)
                    }
                    return super.findDeserializer(type)
                }
            }
        when:
            NestedArrayAddress e = deserializer.deserialize(new BsonReaderDecoder(bsonDocumentAddress.asBsonReader()), decoderContext, Argument.of(NestedArrayAddress))
        then:
            e.lastName == "B"
            e.addresses[0].address == "The home"
            e.firstName == "A"
    }

    def "validate default collections"() {
        given:
            def nullValues = """{"collection":null, "list":null, "set":null, "arrayList":null, "hashMap":null}"""
        when:
            def valueNullables = bsonJsonMapper.readValue("{}", DefaultNullableCollectionsObj)
        then:
            valueNullables.collection == null
            valueNullables.list == null
            valueNullables.set == null
            valueNullables.arrayList == null
            valueNullables.linkedList == null
            valueNullables.hashSet == null
            valueNullables.linkedHashSet == null
            valueNullables.treeSet == null
            valueNullables.hashMap == null
            valueNullables.linkedHashSet == null
            valueNullables.treeSet == null
            valueNullables.hashMap == null
            valueNullables.linkedHashMap == null
            valueNullables.treeMap == null
            !valueNullables.optional.isPresent()
        when:
            def valueNonNulls = bsonJsonMapper.readValue("{}", DefaultNonNullCollectionsObj)
        then:
            valueNonNulls.collection.isEmpty()
            valueNonNulls.list.isEmpty()
            valueNonNulls.list instanceof ArrayList
            valueNonNulls.set.isEmpty()
            valueNonNulls.set instanceof HashSet
            valueNonNulls.arrayList.isEmpty()
            valueNonNulls.linkedList.isEmpty()
            valueNonNulls.hashSet.isEmpty()
            valueNonNulls.linkedHashSet.isEmpty()
            valueNonNulls.treeSet.isEmpty()
            valueNonNulls.hashMap.isEmpty()
            valueNonNulls.linkedHashSet.isEmpty()
            valueNonNulls.treeSet.isEmpty()
            valueNonNulls.hashMap.isEmpty()
            valueNonNulls.linkedHashMap.isEmpty()
            valueNonNulls.treeMap.isEmpty()
            !valueNonNulls.optional.isPresent()
        when:
            def valueNullables2 = bsonJsonMapper.readValue(nullValues, DefaultNullableCollectionsObj)
        then:
            valueNullables.collection == null
            valueNullables.list == null
            valueNullables.set == null
            valueNullables.arrayList == null
            valueNullables.linkedList == null
            valueNullables.hashSet == null
            valueNullables.linkedHashSet == null
            valueNullables.treeSet == null
            valueNullables.hashMap == null
            valueNullables.linkedHashSet == null
            valueNullables.treeSet == null
            valueNullables.hashMap == null
            valueNullables.linkedHashMap == null
            valueNullables.treeMap == null
            !valueNullables.optional.isPresent()
        when:
            def valueNonNulls2 = bsonJsonMapper.readValue(nullValues, DefaultNonNullCollectionsObj)
        then:
            valueNonNulls.collection.isEmpty()
            valueNonNulls.list.isEmpty()
            valueNonNulls.set.isEmpty()
            valueNonNulls.arrayList.isEmpty()
            valueNonNulls.linkedList.isEmpty()
            valueNonNulls.hashSet.isEmpty()
            valueNonNulls.linkedHashSet.isEmpty()
            valueNonNulls.treeSet.isEmpty()
            valueNonNulls.hashMap.isEmpty()
            valueNonNulls.linkedHashSet.isEmpty()
            valueNonNulls.treeSet.isEmpty()
            valueNonNulls.hashMap.isEmpty()
            valueNonNulls.linkedHashMap.isEmpty()
            valueNonNulls.treeMap.isEmpty()
            !valueNonNulls.optional.isPresent()
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

@Serdeable
class NestedObj2Address {

    String firstName
    NestedObjAddress address
    String lastName

}

@Serdeable
class NestedObjAddress {

    String firstName
    Address address
    String lastName

}

@Serdeable
class NestedArrayAddress {

    String firstName
    List<Address> addresses
    String lastName

}

class DelegationDecoderContext implements Deserializer.DecoderContext {

    @Delegate
    private final Deserializer.DecoderContext delegate

    DelegationDecoderContext(Deserializer.DecoderContext delegate) {
        this.delegate = delegate
    }
}

class MappedCodec<T> implements Codec<T> {

    protected final SerdeRegistry serdeRegistry
    protected final Deserializer<T> deserializer
    protected final Class<T> type
    protected final Argument<T> argument

    MappedCodec(SerdeRegistry serdeRegistry, Deserializer<T> deserializer, Class<T> type) {
        this.serdeRegistry = serdeRegistry
        this.deserializer = deserializer
        this.type = type
        this.argument = Argument.of(type)
    }

    @Override
    T decode(BsonReader reader, DecoderContext decoderContext) {
        try {
            T deserialize = deserializer.deserialize(new BsonReaderDecoder(reader), serdeRegistry.newDecoderContext(type), argument)
            return deserialize
        } catch (IOException e) {
            throw new SerdeException("Cannot deserialize: " + type, e)
        }
    }

    @Override
    void encode(BsonWriter writer, T value, EncoderContext encoderContext) {
        throw new SerdeException("Not impl")
    }

    @Override
    Class<T> getEncoderClass() {
        return type
    }
}