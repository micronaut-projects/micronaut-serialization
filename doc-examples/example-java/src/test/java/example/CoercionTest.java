package example;

import io.micronaut.serde.ObjectMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
class CoercionTest {

    @Test
    void testCoercion(ObjectMapper objectMapper) throws IOException {
        Item item = objectMapper.readValue("{\"id\": \"1234\", \"name\": 42, \"count\": 9.75}", Item.class);

        assertEquals(1234, item.id());
        assertEquals("42", item.name());
        assertEquals(9, item.count());
    }
}
