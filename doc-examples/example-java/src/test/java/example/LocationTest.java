package example;

import io.micronaut.serde.ObjectMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
public class LocationTest {
    @Test
    void testLocation(ObjectMapper objectMapper) throws IOException {
        Map<Feature, Point> features =
                Collections.singletonMap(new Feature("Tree"), Point.valueOf(100, 50));
        String result = objectMapper.writeValueAsString(
                new Location(features)
        );
        Location location = objectMapper.readValue(result, Location.class);
        assertNotNull(location);
        assertEquals(1, location.getFeatures().size());
        String name = location.getFeatures().keySet().iterator().next().name();
        assertEquals("Tree", name);
    }
}
