package io.micronaut.serde.jackson

import io.micronaut.http.HttpStatus
import io.micronaut.json.JsonMapper
import io.micronaut.serde.jackson.maps.CustomKeys
import io.micronaut.serde.jackson.maps.EnumKeys
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class JacksonMapSerdeSpec extends Specification implements io.micronaut.serde.JsonSpec {
    @Inject JsonMapper jsonMapper

    void "test serialize / deserialize maps with enum keys"() {
        when:"empty map is written"
        def result = writeJson(jsonMapper, new EnumKeys([:]))

        then:
        result == "{}"



        when:
        result = writeJson( jsonMapper, new EnumKeys([(HttpStatus.OK): 200]))

        then:
        result == '{"statusCodes":{"OK":200}}'

    }

    void "test serialize / deserialize maps with custom keys"() {
        when:"empty map is written"
        def result = writeJson(jsonMapper, new CustomKeys([:]))

        then:
        result == "{}"



        when:
        result = writeJson( jsonMapper, new CustomKeys([(new CustomKeys.CustomKey("foo")): 200]))

        then:
        result == '{"data":{"foo":200}}'

    }
}
