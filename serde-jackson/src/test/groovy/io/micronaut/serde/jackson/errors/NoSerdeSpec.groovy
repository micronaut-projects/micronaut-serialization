package io.micronaut.serde.jackson.errors

import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.exceptions.SerdeException
import io.micronaut.serde.jackson.JsonCompileSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class NoSerdeSpec extends Specification {
    @Inject ObjectMapper objectMapper

    void "test error when no serde is present"() {
        when:
        objectMapper.writeValueAsString(new Foo())

        then:
        def e = thrown(SerdeException)
        e.message == 'No serializable introspection present for type Foo. Consider adding Serdeable.Serializable annotate to type Foo. Alternatively if you are not in control of the project\'s source code, you can use @SerdeImport(Foo.class) to enable serialization of this type.'
    }
    static class Foo {}
}


