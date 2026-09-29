package example

import io.micronaut.serde.cbor.CborObjectMapper
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@MicronautTest
class BookTest {

    @Test
    fun testWriteReadBook(cborObjectMapper: CborObjectMapper) {
        val bytes = cborObjectMapper.writeValueAsBytes(Book("The Stand", 50))
        assertNotNull(bytes)
        assertTrue(bytes.isNotEmpty())
        // CBOR map major type, not JSON text
        assertTrue((bytes[0].toInt() and 0xE0) == 0xA0)

        val book = cborObjectMapper.readValue(bytes, Book::class.java)
        assertNotNull(book)
        assertEquals("The Stand", book!!.title)
        assertEquals(50, book.quantity)
    }
}
