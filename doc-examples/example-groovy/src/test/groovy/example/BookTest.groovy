package example

import io.micronaut.serde.ObjectMapper
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
}