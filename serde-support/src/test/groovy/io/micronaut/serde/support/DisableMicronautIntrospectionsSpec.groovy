package io.micronaut.serde.support

import io.micronaut.context.annotation.Property
import io.micronaut.http.hateoas.JsonError
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.exceptions.SerdeException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
@Property(name = "micronaut.serde.includedIntrospectionPackages", value = "")
class DisableMicronautIntrospectionsSpec extends Specification {

    @Inject ObjectMapper jsonMapper

    void "test read / write JsonError"() {
        given:
        JsonError error = new JsonError("my error")

        when:
        jsonMapper.writeValueAsString(error)

        then:
        def e = thrown(SerdeException)
        e.message.contains("No serializable introspection present for type JsonError")
    }
}
