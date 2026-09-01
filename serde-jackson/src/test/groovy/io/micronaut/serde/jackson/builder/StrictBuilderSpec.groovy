package io.micronaut.serde.jackson.builder

import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class StrictBuilderSpec extends Specification {
    @Inject ObjectMapper objectMapper

    void "test builder fails when a required property is missing"() {
        when:
        objectMapper.readValue('{"owner":"growth"}', TestBuildStrict)

        then:
        def e = thrown(Exception)
        e.message.contains("Required property [String service] is not present in supplied data")
    }

    void "test builder fails when a required property is null"() {
        when:
        objectMapper.readValue('{"service":null}', TestBuildStrict)

        then:
        def e = thrown(Exception)
        e.message.contains("Required property")
    }

    void "test builder applies declared default values for the properties missing from the input"() {
        when:
        def value = objectMapper.readValue('{"service":"checkout"}', TestBuildStrict)

        then: "the declared default values are applied"
        value.service == 'checkout'
        value.owner == 'platform'
        value.retries == 3

        and: "a property without serialization metadata keeps the value assigned by the builder"
        value.notes == 'none'
    }

    void "test builder keeps the supplied values over the declared default values"() {
        when:
        def value = objectMapper.readValue('{"service":"checkout","owner":"growth","retries":10,"notes":"rollback ready"}', TestBuildStrict)

        then:
        value.service == 'checkout'
        value.owner == 'growth'
        value.retries == 10
        value.notes == 'rollback ready'
    }

    void "test builder required property declared on the builder method"() {
        when:
        objectMapper.readValue('{}', TestBuildStrictMethod)

        then:
        def e = thrown(Exception)
        e.message.contains("Required property")

        when:
        def value = objectMapper.readValue('{"service":"checkout"}', TestBuildStrictMethod)

        then:
        value.service == 'checkout'
    }
}
