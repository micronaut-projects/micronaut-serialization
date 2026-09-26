package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

@MicronautTest
class BookHttpTest {

    @Test
    fun cborHttpRoundTrip(client: BookClient) {
        val saved = client.save(Book("The Stand", 50))
        assertNotNull(saved)
        assertEquals("The Stand", saved.title)
        assertEquals(50, saved.quantity)
    }
}
