package io.micronaut.serde.oracle.jdbc.json


import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.bson.Address
import io.micronaut.serde.bson.Person
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import oracle.sql.json.OracleJsonObject
import spock.lang.Specification

import java.nio.charset.StandardCharsets

@MicronautTest
class OracleJsonMappingSpec extends Specification {

    @Inject
    OracleJdbcJsonBinaryObjectMapper osonMapper

    @Inject
    OracleJdbcJsonTextObjectMapper jsonMapper

    @Inject
    SerdeRegistry serdeRegistry

    def "test read inputstream"() {
        when:
            def map = jsonMapper.readValue('{"title": "The Stand", "pages": 454}'.bytes, Map)

        then:
            map.size() == 2

        when:
            map = jsonMapper.readValue(new ByteArrayInputStream('{"title": "The Stand", "pages": 454}'.bytes), Map)

        then:
            map.size() == 2
    }

    def "validate mapping"() {
        given:
            def expectedJson = """{"_id":"12345","firstName":"John","surname":"Smith","addr":{"address":"The home","street":"Downstreet","town":"Paris","postcode":"123456"}}"""
            def person = new Person("12345", "John", "Smith", "p4sw0rd", new Address("The home", "Downstreet", "Paris", "123456"))
        expect:
            asBsonJsonString(person) == expectedJson
            encodeAsBinaryDecodeJson(person) == expectedJson
            encodeAsBinaryDecodeAsObject(person) == {
                person.password = null
                person
            }.call()
    }

    String asBsonJsonString(Object bean) {
        def bytes = jsonMapper.writeValueAsBytes(bean)
        return new String(bytes, StandardCharsets.UTF_8)
    }

    String encodeAsBinaryDecodeJson(Object bean) {
        def bytes = osonMapper.writeValueAsBytes(bean)
        def object = osonMapper.readValue(bytes, OracleJsonObject)
        return new String(jsonMapper.writeValueAsBytes(object), StandardCharsets.UTF_8)
    }

    Object encodeAsBinaryDecodeAsObject(Object bean) {
        def bytes = osonMapper.writeValueAsBytes(bean)
        return osonMapper.readValue(bytes, bean.getClass())
    }

}
