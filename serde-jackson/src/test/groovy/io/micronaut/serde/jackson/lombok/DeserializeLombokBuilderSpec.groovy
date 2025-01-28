package io.micronaut.serde.jackson.lombok

import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Issue
import spock.lang.Specification

@MicronautTest
class DeserializeLombokBuilderSpec extends Specification {
    @Inject
    ObjectMapper objectMapper

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/11538')
    void "test deserialize record with @Builder annotation"() {
        when:
        MyDTO myDTO = objectMapper.readValue(
                '{ "id": 1, "common_field": "any", "common_field2": 10 }',
                MyDTO
        )

        then:
        myDTO != null
        myDTO.id() == 1
        myDTO.commonDTO().commonField() == 'any'
        myDTO.commonDTO().commonField2() == 10
    }
}
