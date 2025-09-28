package io.micronaut.serde.jackson

import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.AbstractBasicSerdeSpec
import io.micronaut.serde.ObjectWithArrayRecordNotNull
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@MicronautTest
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

    def "test nested nullable object with JsonInclude.ALWAYS"() {
        when:
        def obj = new MyBeanWithNestedObject("id1", new MyBeanWithNestedObject.MyNestedBean(new MyBeanWithNestedObject.Key("f1", 1), "name"))
        def json = jsonMapper.writeValueAsString(obj)
        def result = jsonMapper.readValue(json, MyBeanWithNestedObject)
        then:
        obj == result
        when:
        obj = new MyBeanWithNestedObject("id2", null)
        json = jsonMapper.writeValueAsString(obj)
        result = jsonMapper.readValue(json, MyBeanWithNestedObject)
        then:
        obj == result
    }
}
