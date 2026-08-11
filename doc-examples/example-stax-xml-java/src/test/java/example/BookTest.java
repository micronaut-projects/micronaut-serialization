package example;

import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.xml.XmlObjectMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
public class BookTest {

    @Inject
    @Named(XmlObjectMapper.XML_MAPPER_NAME)
    ObjectMapper xmlMapper;

    @Test
    void testWriteReadBook() throws IOException {
        String result = xmlMapper.writeValueAsString(new Book(
            "978-0307743688",
            "The Stand",
            List.of("Stephen King")
        ));

        assertEquals(
            "<book isbn=\"978-0307743688\"><title>The Stand</title><authors><author>Stephen King</author></authors></book>",
            result
        );

        Book book = xmlMapper.readValue(result, Book.class);
        assertNotNull(book);
        assertEquals("978-0307743688", book.getIsbn());
        assertEquals("The Stand", book.getTitle());
        assertEquals(List.of("Stephen King"), book.getAuthors());
    }
}
