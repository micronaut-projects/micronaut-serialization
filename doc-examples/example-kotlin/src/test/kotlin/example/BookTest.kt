package example

import io.micronaut.core.type.Argument
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.Map

@MicronautTest
class BookTest {

    @Test
    fun testWriteReadBook(objectMapper: ObjectMapper) {
        val result = objectMapper.writeValueAsString(Book("The Stand", 50))
        val book = objectMapper.readValue(result, Book::class.java)
        Assertions.assertNotNull(book)
        assertEquals(
            "The Stand", book!!.title
        )
        assertEquals(50, book.quantity)
    }


    @Test
    @Throws(IOException::class)
    fun testListOfBooks(objectMapper: ObjectMapper) {
        val result: String = objectMapper.writeValueAsString(
            listOf(
                Book("The Stand", 50),
                Book("Godfather", 10),
                Book("VALIS", 100)
            )
        )

        val books = objectMapper.readValue(result, Argument.listOf(Book::class.java))!!
        assertEquals(3, books.size)
        val firstBook = books[0]
        assertEquals(
            "The Stand", firstBook.title
        )
        assertEquals(50, firstBook.quantity)
    }

    @Test
    @Throws(IOException::class)
    fun testMapOfBooks(objectMapper: ObjectMapper) {
        val result: String = objectMapper.writeValueAsString(
            Map.of<String?, Book?>(
                "myBook", Book("The Stand", 50),
                "hisBook", Book("Godfather", 10),
                "herBook", Book("VALIS", 100)
            )
        )

        val books = objectMapper.readValue(
            result,
            Argument.mapOf(String::class.java, Book::class.java)
        )!!
        assertEquals(3, books.size)
        val herBook: Book = books["herBook"]!!
        assertEquals("VALIS", herBook.title)
        assertEquals(100, herBook.quantity)
    }

    @Test
    @Throws(IOException::class)
    fun testBoxOfBook(objectMapper: ObjectMapper) {
        val result: String = objectMapper.writeValueAsString(Box(Book("The Stand", 50)))

        val box: Box<Book> = objectMapper.readValue(result, Argument.of(Box::class.java, Book::class.java)) as Box<Book>
        val book = box.item!!
        Assertions.assertNotNull(book)
        assertEquals("The Stand", book.title)
        assertEquals(50, book.quantity)
    }

    @Serdeable
    data class Box<I>(val item: I?)
}
