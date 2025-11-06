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
        json == """{"id":"id1","key":{"strId":"f1","longId":1},"name":"name"}"""
        when:
        obj = new MyBeanWithNestedObject("id2", null)
        json = jsonMapper.writeValueAsString(obj)
        result = jsonMapper.readValue(json, MyBeanWithNestedObject)
        then: "The result is different, as always has added the type"
        def expected = new MyBeanWithNestedObject("id2", new MyBeanWithNestedObject.MyNestedBean(null, null))
        expected == result
        json == """{"id":"id2"}"""
    }

    def "test nested non-null object with JsonInclude.ALWAYS"() {
        when:
        def obj = new MyBeanWithNestedObjectNonNull("id1", new MyBeanWithNestedObjectNonNull.MyNestedBean(new MyBeanWithNestedObjectNonNull.Key("f1", 1), "name"))
        def json = jsonMapper.writeValueAsString(obj)
        def result = jsonMapper.readValue(json, MyBeanWithNestedObjectNonNull)
        then:
        obj == result
        json == """{"id":"id1","key":{"strId":"f1","longId":1},"name":"name"}"""
        when:
        obj = new MyBeanWithNestedObjectNonNull("id2", new MyBeanWithNestedObjectNonNull.MyNestedBean(null, null))
        json = jsonMapper.writeValueAsString(obj)
        result = jsonMapper.readValue(json, MyBeanWithNestedObjectNonNull)
        then:
        obj == result
        json == """{"id":"id2","key":null,"name":null}"""
    }

    def "test object with non null map and JsonInclude.ALWAYS"() {
        when:
        def obj = new MyBeanWithMap("id1", 1, new MyBeanWithMap.MyNestedBeanWithMap(1L, Map.of("key", "val")))
        def json = jsonMapper.writeValueAsString(obj)
        def result = jsonMapper.readValue(json, MyBeanWithMap)
        then:
        obj == result
        json == """{"fooBar":"id1","abcXyz":1,"nested":{"id":1,"key":"val"}}"""
    }

    def "test object with null map and JsonInclude.ALWAYS"() {
        when:
        def obj = new MyBeanWithMap("id2", 2, new MyBeanWithMap.MyNestedBeanWithMap(2L, null))
        def json = jsonMapper.writeValueAsString(obj)
        def result = jsonMapper.readValue(json, MyBeanWithMap)
        then:
        obj == result
        json == """{"fooBar":"id2","abcXyz":2,"nested":{"id":2}}"""
    }
}
