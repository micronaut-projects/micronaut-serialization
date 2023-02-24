package example

import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class PersonFilterTest extends Specification {
    @Inject ObjectMapper objectMapper

    void "test write person without preferred name"() {
        when:
        String result = objectMapper.writeValueAsString(new Person(name: "Adam"))

        then:
        '{"name":"Adam"}' == result
    }

    void "test write person with preferred name"() {
        when:
        String result = objectMapper.writeValueAsString(new Person(name: "Adam", preferredName: "Ad"))

        then:
        '{"preferredName":"Ad"}' == result
    }
}
