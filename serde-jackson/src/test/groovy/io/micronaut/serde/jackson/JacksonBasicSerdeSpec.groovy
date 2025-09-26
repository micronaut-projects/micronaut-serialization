package io.micronaut.serde.jackson

import io.micronaut.context.annotation.Property
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.AbstractBasicSerdeSpec
import io.micronaut.serde.ObjectWithArrayRecordNotNull
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

import java.time.Instant

@MicronautTest
@Property(name = "micronaut.serde.deserialization.ignore-unknown", value = "false")
class JacksonBasicSerdeSpec extends AbstractBasicSerdeSpec {

    @Inject
    JsonMapper jsonMapper

    def "empty list - record not null"() {
        given:
            def json = """{}"""
        when:
            def obj = jsonMapper.readValue(jsonAsBytes(json), Argument.of(ObjectWithArrayRecordNotNull))
        then:
            obj
            obj.vals() == []
    }

    def "record with multiple constructors"() {
        when:
        def myRecord = new MyRecord("value", 10, Date.from(Instant.now().minusSeconds(60)))
        def str = jsonMapper.writeValueAsString(myRecord)
        def result = jsonMapper.readValue(str, MyRecord)
        then:
        myRecord == result
    }
}
