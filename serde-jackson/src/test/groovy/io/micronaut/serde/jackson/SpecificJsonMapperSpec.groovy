package io.micronaut.serde.jackson

import io.micronaut.core.type.Argument
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class SpecificJsonMapperSpec extends Specification {

    @Inject ObjectMapper objectMapper

    void "test specific mapper"() {
        when:
            def specific = objectMapper.createSpecific(Argument.of(TestX))
        then:
            specific.writeValueAsString(new TestX(name: "Fred")) == '{"name":"Fred"}'
            specific.@specificType
            specific.@specificSerializer
            specific.@specificDeserializer

    }

    @Serdeable
    static class TestX {
        String name
    }

}
