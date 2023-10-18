package example

import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@MicronautTest
class PointTest {
    @Test
    fun testWriteReadPoint(objectMapper: ObjectMapper) {
        val result = objectMapper.writeValueAsString(
            Point.valueOf(50, 100)
        )
        val point = objectMapper.readValue(result, Point::class.java)
        Assertions.assertNotNull(point)
        val coords = point.coords()
        Assertions.assertEquals(50, coords[0])
        Assertions.assertEquals(100, coords[1])
    }
}