package io.micronaut.serde.jackson.builder

import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class BuilderSpec extends Specification {
    @Inject ObjectMapper objectMapper

    void "test serialize/deserialize builder"() {
        given:
        def json = '{"name":"Fred","age":30}'

        when:
        def value = objectMapper.readValue(json, TestBuildMe)

        then:
        value.name == 'Fred'
        value.age == 30

        when:
        def result = objectMapper.writeValueAsString(value)

        then:
        result == json
    }
}
