package example;

import io.micronaut.serde.ObjectMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
public class PointTest {

    @Test
    void testWriteReadPoint(ObjectMapper objectMapper) throws IOException {
        String result = objectMapper.writeValueAsString(
                Point.valueOf(50, 100)
        );
        Point point = objectMapper.readValue(result, Point.class);
        assertNotNull(point);
        int[] coords = point.coords();
        assertEquals(50, coords[0]);
        assertEquals(100, coords[1]);
    }
}
