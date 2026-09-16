package example

import io.micronaut.serde.cbor.CborObjectMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class BookTest extends Specification {
    @Inject CborObjectMapper cborObjectMapper

    void "test read/write book"() {
        when:
        byte[] bytes = cborObjectMapper.writeValueAsBytes(new Book("The Stand", 50))

        then:
        bytes != null
        bytes.length > 0
        // CBOR map major type, not JSON text
        (bytes[0] & 0xE0) == 0xA0

        when:
        Book book = cborObjectMapper.readValue(bytes, Book)

        then:
        book != null
        book.title == "The Stand"
        book.quantity == 50
    }
}
