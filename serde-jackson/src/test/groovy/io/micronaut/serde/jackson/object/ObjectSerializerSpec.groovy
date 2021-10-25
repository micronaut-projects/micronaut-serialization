package io.micronaut.serde.jackson.object

import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.jackson.JsonSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest
class ObjectSerializerSpec extends Specification implements JsonSpec {
    @Inject @Shared JsonMapper jsonMapper

    void "test write simple"() {
        when:
        def bean = new Simple(name: "Test")
        def result = writeJson(jsonMapper, bean)

        then:
        result == '{"name":"Test"}'
    }

    void "test read/write constructor args"() {
        when:
        def bean = new ConstructorArgs("test", 100)
        bean.author = "Bob"
        bean.other = "Something"
        def result = writeJson(jsonMapper, bean)

        then:
        result == '{"title":"test","pages":100,"author":"Bob","other":"Something"}'

        when:
        bean = jsonMapper.readValue(result, Argument.of(ConstructorArgs))

        then:
        bean.title == 'test'
        bean.pages == 100
        bean.other == 'Something'
        bean.author == 'Bob'

        when:
        bean = jsonMapper.readValue('{"other":"Something","author":"Bob", "title":"test","pages":100}', Argument.of(ConstructorArgs))

        then:
        bean.title == 'test'
        bean.pages == 100
        bean.other == 'Something'
        bean.author == 'Bob'
    }
}