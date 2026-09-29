package example

import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@MicronautTest
class ProductTest {

    @Test
    fun testSerDeser(objectMapper: ObjectMapper) {
        val result = objectMapper.writeValueAsString(Product("Apple", 10))
        assertEquals(
            "{\"p_name\":\"Apple\",\"p_quantity\":10}",
            result
        )
        val product = objectMapper.readValue(result, Product::class.java)!!
        assertEquals(
            "Apple",
            product.name
        )
    }
}
