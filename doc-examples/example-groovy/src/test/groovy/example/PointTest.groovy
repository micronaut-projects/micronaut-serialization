package example

import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class PointTest extends Specification {
    @Inject ObjectMapper objectMapper

    void "test read/write point"() {
        given:
        String result = objectMapper.writeValueAsString(
                Point.valueOf(50, 100)
        )
        Point point = objectMapper.readValue(result, Point.class)

        expect:
        point != null
        int[] coords = point.coords()
        coords[0] == 50
        coords[1] == 100
    }
}
