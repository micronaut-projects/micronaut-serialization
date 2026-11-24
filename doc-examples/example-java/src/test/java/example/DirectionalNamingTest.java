package example;

import io.micronaut.serde.ObjectMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
class DirectionalNamingTest {

    @Test
    void testDirectionalNaming(ObjectMapper objectMapper) throws IOException {
        String json = objectMapper.writeValueAsString(new DirectionalContact("John", "Doe"));

        assertEquals("{\"first_name\":\"John\",\"last_name\":\"Doe\"}", json);

        DirectionalContact contact = objectMapper.readValue(
            "{\"FirstName\":\"Jane\",\"LastName\":\"Smith\"}",
            DirectionalContact.class
        );

        assertEquals("Jane", contact.firstName());
        assertEquals("Smith", contact.lastName());
    }
}
