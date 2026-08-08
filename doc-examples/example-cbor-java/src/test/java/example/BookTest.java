package example;

import io.micronaut.serde.cbor.CborObjectMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
public class BookTest {

    @Test
    void testWriteReadBook(CborObjectMapper cborObjectMapper) throws IOException {
        byte[] bytes = cborObjectMapper.writeValueAsBytes(new Book("The Stand", 50));
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
        // CBOR map major type, not JSON text
        assertTrue((bytes[0] & 0xE0) == 0xA0);

        Book book = cborObjectMapper.readValue(bytes, Book.class);
        assertNotNull(book);
        assertEquals("The Stand", book.getTitle());
        assertEquals(50, book.getQuantity());
    }
}
