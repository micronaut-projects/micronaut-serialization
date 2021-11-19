package io.micronaut.serde.support

import io.micronaut.core.type.Argument
import io.micronaut.http.hateoas.JsonError
import io.micronaut.http.hateoas.Link
import io.micronaut.json.JsonMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import java.nio.charset.StandardCharsets

@MicronautTest
class CoreTypeSerdeSpec extends Specification {
    @Inject JsonMapper jsonMapper

    void "test read / write JsonError"() {
        given:
        JsonError error = new JsonError("my error")
        error.link(Link.SELF, "http://test")
        error.embedded("info", new MyInfo("Additional Info"))

        when:
        def result = writeJson(error)
        then:
        result == '{"message":"my error","_links":{"self":[{"href":"http://test","templated":false}]},"_embedded":{"info":[{"info":"Additional Info"}]}}'

        when:
        def read = jsonMapper.readValue(result, Argument.of(JsonError))

        then:
        read != null
        read.message == 'my error'
//      TODO: fix me?
//        read.links.size() == 1
//        read.embedded.size() == 1
    }

    private String writeJson(JsonError error) {
        new String(jsonMapper.writeValueAsBytes(error), StandardCharsets.UTF_8)
    }
}
