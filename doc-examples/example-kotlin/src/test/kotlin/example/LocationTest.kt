package example

import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@MicronautTest
class LocationTest {
    @Test
    fun testLocation(objectMapper: ObjectMapper) {
        val features = mapOf(Feature("Tree") to Point.valueOf(100, 50))
        val result = objectMapper.writeValueAsString(
            Location(features)
        )
        val location = objectMapper.readValue(result, Location::class.java)
        Assertions.assertNotNull(location)
        Assertions.assertEquals(1, location.features.size)
        val name: String = location.features.keys.first().name()
        Assertions.assertEquals("Tree", name)
    }
}