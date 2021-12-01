package example


import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class LocationTest extends Specification {
    @Inject ObjectMapper objectMapper

    void "test read/write location"() {
        when:
        String result = objectMapper.writeValueAsString(
                new Location((new Feature("Tree")) : Point.valueOf(100, 50))
        );
        Location location = objectMapper.readValue(result, Location)

        then:
        location != null
        location.features.size() == 1
        location.features.keySet().first().name() == 'Tree'
    }
}
