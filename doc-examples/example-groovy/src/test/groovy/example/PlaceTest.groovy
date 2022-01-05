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
        String result = objectMapper.writeValueAsString(new Place(Point.valueOf(50, 100), Point.valueOf(1, 2), Point.valueOf(3, 4)))
        final Place place = objectMapper.readValue(result, Place.class)

        then:
        place != null
        place.point.coords()[0] == 50
        place.point.coords()[1] == 100
        place.pointCustomSer.coords()[0] == 2
        place.pointCustomSer.coords()[1] == 1
        place.pointCustomDes.coords()[0] == 4
        place.pointCustomDes.coords()[1] == 3
        result == '{"point":[100,50],"pointCustomSer":[2,1],"pointCustomDes":[3,4]}'
    }
}
