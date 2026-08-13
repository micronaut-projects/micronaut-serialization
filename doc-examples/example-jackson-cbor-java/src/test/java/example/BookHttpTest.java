package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
class BookHttpTest {

    @Test
    void cborHttpRoundTrip(BookClient client) {
        Book saved = client.save(new Book("The Stand", 50));
        assertNotNull(saved);
        assertEquals("The Stand", saved.getTitle());
        assertEquals(50, saved.getQuantity());
    }
}
