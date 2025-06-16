package example;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
public class BookTest {

    @Inject
    ObjectMapper objectMapper;

    @Test
    void testWriteReadBook() throws IOException {
        String result = objectMapper.writeValueAsString(new Book("The Stand", 50));

        Book book = objectMapper.readValue(result, Book.class);
        assertNotNull(book);
        assertEquals(
                "The Stand", book.getTitle()
        );
        assertEquals(50, book.getQuantity());
    }

    @Test
    void testListOfBooks() throws IOException {
        String result = objectMapper.writeValueAsString(List.of(
            new Book("The Stand", 50),
            new Book("Godfather", 10),
            new Book("VALIS", 100)
        ));

        List<Book> books = objectMapper.readValue(result, Argument.listOf(Book.class));
        assertEquals(3, books.size());
        Book firstBook = books.get(0);
        assertEquals(
                "The Stand", firstBook.getTitle()
        );
        assertEquals(50, firstBook.getQuantity());
    }

    @Test
    void testMapOfBooks() throws IOException {
        String result = objectMapper.writeValueAsString(Map.of(
            "myBook", new Book("The Stand", 50),
            "hisBook", new Book("Godfather", 10),
            "herBook", new Book("VALIS", 100)
        ));

        Map<String, Book> books = objectMapper.readValue(result, Argument.mapOf(String.class, Book.class));
        assertEquals(3, books.size());
        Book herBook = books.get("herBook");
        assertEquals(
            "VALIS", herBook.getTitle()
        );
        assertEquals(100, herBook.getQuantity());
    }

    @Test
    void testBoxOfBook() throws IOException {
        String result = objectMapper.writeValueAsString(new Box<>(new Book("The Stand", 50)));

        Box<Book> box = objectMapper.readValue(result, Argument.of(Box.class, Book.class));
        Book book = box.item();
        assertNotNull(book);
        assertEquals(
            "The Stand", book.getTitle()
        );
        assertEquals(50, book.getQuantity());
    }

    @Serdeable
    public record Box<I>(I item) {
    }

}
