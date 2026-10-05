package example

import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class CoercionTest extends Specification {
    @Inject ObjectMapper objectMapper

    void "test coercion"() {
        when:
        Item item = objectMapper.readValue('{"id": "1234", "name": 42, "count": 9.75}', Item)

        then:
        item.id == 1234
        item.name == "42"
        item.count == 9
    }
}
