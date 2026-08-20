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

    @Test
    void testWriteReadJaxbBook() throws IOException {
        JaxbBook input = new JaxbBook();
        input.isbn = "978-0307743688";
        input.title = "The Stand";
        input.authors = List.of("Stephen King");

        String result = xmlMapper.writeValueAsString(input);

        assertEquals(
            "<book isbn=\"978-0307743688\"><title>The Stand</title><subtitle xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:nil=\"true\"></subtitle><author>Stephen King</author></book>",
            result
        );

        JaxbBook book = xmlMapper.readValue(result, JaxbBook.class);
        assertEquals(input.isbn, book.isbn);
        assertEquals(input.title, book.title);
        assertEquals("Untitled", book.subtitle);
        assertEquals(input.authors, book.authors);
    }
}
