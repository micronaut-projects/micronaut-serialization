package example;

import java.io.IOException;

import io.micronaut.serde.ObjectMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
public class PlaceTest {
    @Test
    void testPlace(ObjectMapper objectMapper) throws IOException {
        String result = objectMapper.writeValueAsString(new Place(Point.valueOf(50, 100)));
        final Place place = objectMapper.readValue(result, Place.class);
        assertNotNull(place);
        assertEquals(100, place.getPoint().coords()[0]);
        assertEquals(50, place.getPoint().coords()[1]);
    }
}
