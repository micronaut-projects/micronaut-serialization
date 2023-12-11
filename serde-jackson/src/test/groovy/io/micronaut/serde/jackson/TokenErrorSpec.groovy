package io.micronaut.serde.jackson

import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.jackson.jsonvalue.TokenError
import spock.lang.Issue
import spock.lang.Specification

class TokenErrorSpec extends Specification {

    @Issue("https://github.com/micronaut-projects/micronaut-serialization/issues/297")
    void "TokenError should be deserializable from a string"() {
        setup:
        ObjectMapper objectMapper = ObjectMapper.getDefault()

        when:
        TokenError deserializationResult = objectMapper.readValue('"unauthorized_client"', TokenError)

        then:
        deserializationResult == TokenError.UNAUTHORIZED_CLIENT

        when:
        def value = objectMapper.writeValueAsString(TokenError.UNAUTHORIZED_CLIENT)

        then:
        value == '"unauthorized_client"'
    }
}
