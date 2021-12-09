package example

import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class PlaceTest extends Specification {
    @Inject ObjectMapper objectMapper

    void "test read/write place"() {
        when:
        String result = objectMapper.writeValueAsString(new Place(Point.valueOf(50, 100)))
        final Place place = objectMapper.readValue(result, Place.class)


        then:
        place != null
        place.point.coords()[0] == 100
        place.point.coords()[1] == 50
    }
}
