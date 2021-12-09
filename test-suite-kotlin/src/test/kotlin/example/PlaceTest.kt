package example

import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@MicronautTest
class PlaceTest {
    @Test
    fun testPlace(objectMapper: ObjectMapper) {
        val result = objectMapper.writeValueAsString(Place(Point.valueOf(50, 100)))
        val place = objectMapper.readValue(result, Place::class.java)
        Assertions.assertNotNull(place)
        Assertions.assertEquals(100, place.point.coords()[0])
        Assertions.assertEquals(50, place.point.coords()[1])
    }
}