package example

import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@MicronautTest
class BookTest {
    @Test
    fun testWriteReadBook(objectMapper: ObjectMapper) {
        val result = objectMapper.writeValueAsString(Book("The Stand", 50))
        val book = objectMapper.readValue(result, Book::class.java)
        Assertions.assertNotNull(book)
        Assertions.assertEquals(
            "The Stand", book.title
        )
        Assertions.assertEquals(50, book.quantity)
    }
}