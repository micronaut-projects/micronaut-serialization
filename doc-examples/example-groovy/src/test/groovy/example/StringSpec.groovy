package example

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
@Property(name = "spec.name", value = "StringSpec")
class StringSpec extends Specification {

    @Inject
    EmbeddedServer server

    void "test string"() {
        when:
        def client = server.applicationContext.createBean(HttpClient, server.URL)
        def response = client.toBlocking().retrieve("/string")

        then:
        response == '{"woo":"something cool"}'
    }

    @Controller("/string")
    @Requires(property = "spec.name" , value = "StringSpec")
    static class StringController {

        @Get
        def index() {
            return [
                    woo: "${'something'} cool",
            ]
        }
    }
}
