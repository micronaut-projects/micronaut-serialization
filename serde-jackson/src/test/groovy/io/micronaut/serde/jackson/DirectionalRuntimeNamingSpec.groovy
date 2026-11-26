package io.micronaut.serde.jackson

import io.micronaut.json.JsonMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class DirectionalRuntimeNamingSpec extends Specification {

    @Inject
    JsonMapper jsonMapper

    void "a property written and read with naming strategies looked up at runtime"() {
        given:
        def bean = new DirectionalRuntimeNaming.PropertyNamed(firstName: 'John')

        expect:
        jsonMapper.writeValueAsString(bean) == '{"out_firstName":"John"}'
        jsonMapper.readValue('{"in_firstName":"Jane"}', DirectionalRuntimeNaming.PropertyNamed).firstName == 'Jane'
        jsonMapper.readValue('{"out_firstName":"Jane"}', DirectionalRuntimeNaming.PropertyNamed).firstName == null
    }

    void "a type written and read with naming strategies looked up at runtime"() {
        given:
        def bean = new DirectionalRuntimeNaming.TypeNamed(lastName: 'Doe')

        expect:
        jsonMapper.writeValueAsString(bean) == '{"out_lastName":"Doe"}'
        jsonMapper.readValue('{"in_lastName":"Smith"}', DirectionalRuntimeNaming.TypeNamed).lastName == 'Smith'
        jsonMapper.readValue('{"out_lastName":"Smith"}', DirectionalRuntimeNaming.TypeNamed).lastName == null
    }
}
