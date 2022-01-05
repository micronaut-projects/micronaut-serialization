package example;

import io.micronaut.serde.ObjectMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
public class BookTest {

    @Test
    void testWriteReadBook(ObjectMapper objectMapper) throws IOException {
        String result = objectMapper.writeValueAsString(new Book("The Stand", 50));

        Book book = objectMapper.readValue(result, Book.class);
        assertNotNull(book);
        assertEquals(
                "The Stand", book.getTitle()
        );
        assertEquals(50, book.getQuantity());
    }
}
