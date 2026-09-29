package example

import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.xml.XmlObjectMapper
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Named
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

@MicronautTest
class BookTest {

    @Inject
    @field:Named(XmlObjectMapper.XML_MAPPER_NAME)
    lateinit var xmlMapper: ObjectMapper

    @Test
    fun testWriteReadBook() {
        val result = xmlMapper.writeValueAsString(Book(
            "978-0307743688",
            "The Stand",
            listOf("Stephen King")
        ))

        assertEquals(
            "<book isbn=\"978-0307743688\"><title>The Stand</title><authors><author>Stephen King</author></authors></book>",
            result
        )

        val book = xmlMapper.readValue(result, Book::class.java)
        assertNotNull(book)
        assertEquals("978-0307743688", book!!.isbn)
        assertEquals("The Stand", book.title)
        assertEquals(listOf("Stephen King"), book.authors)
    }

    @Test
    fun testWriteReadJaxbBook() {
        val input = JaxbBook()
        input.isbn = "978-0307743688"
        input.title = "The Stand"
        input.authors = listOf("Stephen King")

        val result = xmlMapper.writeValueAsString(input)

        assertEquals(
            "<book isbn=\"978-0307743688\"><title>The Stand</title><subtitle xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:nil=\"true\"></subtitle><author>Stephen King</author></book>",
            result
        )

        val book = xmlMapper.readValue(result, JaxbBook::class.java)!!
        assertEquals(input.isbn, book.isbn)
        assertEquals(input.title, book.title)
        assertEquals("Untitled", book.subtitle)
        assertEquals(input.authors, book.authors)
    }
}
