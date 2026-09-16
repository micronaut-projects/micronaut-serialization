package example

import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@MicronautTest
class CoercionTest {

    @Test
    fun testCoercion(objectMapper: ObjectMapper) {
        val item = objectMapper.readValue("{\"id\": \"1234\", \"name\": 42, \"count\": 9.75}", Item::class.java)!!

        assertEquals(1234, item.id)
        assertEquals("42", item.name)
        assertEquals(9, item.count)
    }
}
