package io.micronaut.serde.support

import io.micronaut.core.type.Argument
import io.micronaut.health.HealthStatus
import io.micronaut.http.hateoas.JsonError
import io.micronaut.http.hateoas.Link
import io.micronaut.management.health.indicator.HealthResult
import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.PendingFeature
import spock.lang.Specification

import java.nio.charset.StandardCharsets

@MicronautTest
class CoreTypeSerdeSpec extends Specification {
    @Inject ObjectMapper jsonMapper

    void "test read / write health result"() {
        given:
        HealthResult hr = HealthResult.builder("db", HealthStatus.DOWN)
            .details(Collections.singletonMap("foo", "bar"))
            .build()

        when:
        def result = writeJson(hr)

        then:
        result == '{"name":"db","status":"DOWN","details":{"foo":"bar"}}'

        when:
        hr = jsonMapper.readValue(result, Argument.of(HealthResult))

        then:
        hr.name == 'db'
        hr.status == HealthStatus.DOWN
    }

    @PendingFeature
    void "test read / write JsonError"() {
        given:
        JsonError error = new JsonError("my error")
        error.link(Link.SELF, "http://test")
        error.embedded("info", new MyInfo("Additional Info").link(Link.SELF, "http://info.com"))

        when:
        def result = writeJson(error)
        then:
        result == '{"message":"my error","_links":{"self":[{"href":"http://test","templated":false}]},"_embedded":{"info":[{"info":"Additional Info","_links":{"self":[{"href":"http://info.com","templated":false}]}}]}}'

        when:
        def read = jsonMapper.readValue(result, Argument.of(JsonError))

        then:
        read != null
        read.message == 'my error'
//      TODO: fix me?
//        read.links.size() == 1
//        read.embedded.size() == 1
    }

    private String writeJson(Object o) {
        new String(jsonMapper.writeValueAsBytes(o), StandardCharsets.UTF_8)
    }
}
