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

    void "test serialize/deserialize builder with @JsonPOJOBuilder"() {
        given:
        def json = '{"name":"Fred","age":30}'

        when:
        def value = objectMapper.readValue(json, TestBuildMe2)

        then:
        value.name == 'Fred'
        value.age == 30

        when:
        def result = objectMapper.writeValueAsString(value)

        then:
        result == json
    }

    void "test deserialize builder on subtype"() {
        given:
        def json = '{"sub":{"foo":"fizz","bar":"buzz"}}'

        when:
        def value = objectMapper.readValue(json, TestBuildSupertype)

        then:
        value.foo == 'fizz'
        value.bar == 'buzz'
    }

    void "test deserialize builder on supertype"() {
        given:
        def json = '{"foo":"fizz"}'

        when:
        def value = objectMapper.readValue(json, TestBuildSupertype2)

        then:
        value.getClass() == TestBuildSupertype2
        value.foo == 'fizz'
    }

    void "test deserialize builder with JsonProperty on outer class"() {
        given:
        def json = '{"bar":"baz"}'

        when:
        def value = objectMapper.readValue(json, TestBuildName)

        then:
        value.foo == 'baz'
    }
}
