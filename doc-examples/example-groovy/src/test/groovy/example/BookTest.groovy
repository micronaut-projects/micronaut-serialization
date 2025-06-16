package example


import io.micronaut.core.type.Argument
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class BookTest extends Specification {
    @Inject ObjectMapper objectMapper

    void "test read/write book"() {
        when:
        String result = objectMapper.writeValueAsString(new Book("The Stand", 50));
        Book book = objectMapper.readValue(result, Book.class);

        then:
        book != null
        book.title == "The Stand"
        book.quantity == 50
    }

    void "test list of books"() throws IOException {
        when:
        String result = objectMapper.writeValueAsString(List.of(
                new Book("The Stand", 50),
                new Book("Godfather", 10),
                new Book("VALIS", 100)
        ));

        List<Book> books = objectMapper.readValue(result, Argument.listOf(Book.class));
        then:
        books.size() == 3
        Book firstBook = books.get(0);
        firstBook != null
        firstBook.title == "The Stand"
        firstBook.quantity == 50
    }

    void "test map of books"() throws IOException {
        when:
        String result = objectMapper.writeValueAsString(Map.of(
                "myBook", new Book("The Stand", 50),
                "hisBook", new Book("Godfather", 10),
                "herBook", new Book("VALIS", 100)
        ));

        Map<String, Book> books = objectMapper.readValue(result, Argument.mapOf(String.class, Book.class));
        then:
        books.size() == 3
        Book herBook = books.get("herBook");
        herBook.getTitle() == "VALIS"
        herBook.getQuantity() == 100
    }

    void "test a box of a book"() throws IOException {
        when:
        String result = objectMapper.writeValueAsString(new Box<>(new Book("The Stand", 50)));

        Box<Book> box = objectMapper.readValue(result, Argument.of(Box.class, Book.class));
        then:
        Book book = box.item
        book.getTitle() == "The Stand"
        book.getQuantity() == 50
    }

    @Serdeable
    static class Box<I> {
        I item

        Box(I item) {
            this.item = item
        }
    }
}
