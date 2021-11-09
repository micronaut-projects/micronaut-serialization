package io.micronaut.serde.bson

import io.micronaut.core.type.Argument
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.bson.BsonDocument
import org.bson.BsonObjectId
import org.bson.types.ObjectId
import spock.lang.Specification

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

}
