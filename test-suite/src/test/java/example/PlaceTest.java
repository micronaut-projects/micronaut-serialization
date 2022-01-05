package example;

import io.micronaut.serde.ObjectMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
public class PlaceTest {
    @Test
    void testPlace(ObjectMapper objectMapper) throws IOException {
        String result = objectMapper.writeValueAsString(new Place(Point.valueOf(50, 100), Point.valueOf(1, 2), Point.valueOf(3, 4)));
        final Place place = objectMapper.readValue(result, Place.class);
        assertNotNull(place);
        assertEquals(50, place.getPoint().coords()[0]);
        assertEquals(100, place.getPoint().coords()[1]);
        assertEquals(2, place.getPointCustomSer().coords()[0]);
        assertEquals(1, place.getPointCustomSer().coords()[1]);
        assertEquals(4, place.getPointCustomDes().coords()[0]);
        assertEquals(3, place.getPointCustomDes().coords()[1]);
        assertEquals("{\"point\":[100,50],\"pointCustomSer\":[2,1],\"pointCustomDes\":[3,4]}", result);
    }
}
