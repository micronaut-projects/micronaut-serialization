package example;

import io.micronaut.serde.ObjectMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
public class PersonFilterTest {

    @Test
    void testWritePersonWithoutPreferredName(ObjectMapper objectMapper) throws IOException {
        String result = objectMapper.writeValueAsString(new Person("Adam", null));
        assertEquals("{\"name\":\"Adam\"}", result);
    }

    @Test
    void testWritePersonWithPreferredName(ObjectMapper objectMapper) throws IOException {
        String result = objectMapper.writeValueAsString(new Person("Adam", "Ad"));
        assertEquals("{\"preferredName\":\"Ad\"}", result);
    }
}
