package io.micronaut.serde.jackson

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.serde.jackson.jsonanygetter.Token
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.PendingFeature
import spock.lang.Specification

@Property(name = "spec.name", value = "JsonAnyGetterSpec")
@MicronautTest
class JsonAnyGetterSpec extends Specification {

    @Inject
    @Client("/")
    HttpClient httpClient;

    @PendingFeature(reason = "https://github.com/micronaut-projects/micronaut-serialization/issues/299")
    void "JsonAnyGetter works"() {
        when:
        BlockingHttpClient client = httpClient.toBlocking()
        HttpResponse<Map> response = client.exchange(HttpRequest.GET("/"), Map)

        then:
        noExceptionThrown()
        HttpStatus.OK == response.getStatus()
        response.getBody().get().get("roles")
    }

    @Requires(property = "spec.name", value = "JsonAnyGetterSpec")
    @Controller
    static class HomeController {
        @Get
        Token index() {
            new Token(Collections.singletonMap("roles", Collections.singletonList("ADMIN")))
        }
    }
}

