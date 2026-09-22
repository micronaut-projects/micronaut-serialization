package example.resources

import io.micronaut.core.type.Argument
import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The Kotlin types compile with a Kotlin Sourcegen backend on the KSP classpath, and are serialized by
 * the runtime serdes.
 */
@MicronautTest
class ResourcesTest {

    @Test
    fun testBook(objectMapper: ObjectMapper) {
        val json = objectMapper.writeValueAsString(Book(null, "Dune", "fherbert"))

        assertEquals("""{"title":"Dune","author":"fherbert"}""", json)
        assertEquals(Book(null, "Dune", "fherbert"), objectMapper.readValue(json, Book::class.java))
        assertEquals(Book(1, "Dune", "fherbert"), objectMapper.readValue("""{"id":1,"title":"Dune","author":"fherbert"}""", Book::class.java))
    }

    @Test
    fun testAuthor(objectMapper: ObjectMapper) {
        val json = objectMapper.writeValueAsString(Author("fherbert", "Frank Herbert"))

        assertEquals("""{"username":"fherbert","name":"Frank Herbert"}""", json)
        assertEquals(Author("fherbert", "Frank Herbert"), objectMapper.readValue(json, Author::class.java))
    }

    @Test
    fun testEntityResource(objectMapper: ObjectMapper) {
        val json = objectMapper.writeValueAsString(EntityResource(Author("fherbert", "Frank Herbert")))

        assertEquals("""{"username":"fherbert","name":"Frank Herbert"}""", json)
        val resource = objectMapper.readValue(json, Argument.of(EntityResource::class.java, Author::class.java))!!
        assertEquals(Author("fherbert", "Frank Herbert"), resource.content)
    }

    @Test
    fun testCollectionResource(objectMapper: ObjectMapper) {
        val collection = CollectionResource(listOf(EntityResource(Book(1, "Dune", "fherbert"))), 1)
        val json = objectMapper.writeValueAsString(collection)

        assertEquals("""{"items":[{"id":1,"title":"Dune","author":"fherbert"}],"total":1}""", json)
        val read = objectMapper.readValue(json, Argument.of(CollectionResource::class.java, Book::class.java))!!
        assertEquals(1, read.total)
        assertEquals(Book(1, "Dune", "fherbert"), (read.items.single() as EntityResource<*>).content)
    }

    @Test
    fun testBookPage(objectMapper: ObjectMapper) {
        val page = BookPage(
            listOf(EntityResource(Book(1, "Dune", "fherbert"))),
            mapOf("fherbert" to EntityResource(Author("fherbert", "Frank Herbert")))
        )
        val json = objectMapper.writeValueAsString(page)

        assertEquals(
            """{"books":[{"id":1,"title":"Dune","author":"fherbert"}],"authors":{"fherbert":{"username":"fherbert","name":"Frank Herbert"}}}""",
            json
        )
        val read = objectMapper.readValue(json, BookPage::class.java)!!
        assertEquals(Book(1, "Dune", "fherbert"), read.books.single().content)
        assertEquals(Author("fherbert", "Frank Herbert"), read.authors.getValue("fherbert").content)
    }
}
