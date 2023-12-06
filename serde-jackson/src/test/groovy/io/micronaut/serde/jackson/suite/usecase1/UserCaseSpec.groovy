package io.micronaut.serde.jackson.suite.usecase1


import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class UserCaseSpec extends Specification {

    @Inject
    ObjectMapper objectMapper

    void "test use-case"() {
        when:
            var source = new RequestWrapper(
                    new RequestType1(
                            "lol", "omg", new OutputSettings()
                    )
            )
            var json = objectMapper.writeValueAsString(source)
            var obj = objectMapper.readValue(json, RequestWrapper.class)
        then:
            obj.request().value1 == "lol"
            obj.request().value2 == "omg"
            json == '{"request":{"@type":"RequestType1","value1":"lol","value2":"omg","output_settings":{}}}'
    }
}
