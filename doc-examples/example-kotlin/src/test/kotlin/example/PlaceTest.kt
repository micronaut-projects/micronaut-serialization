package example

import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@MicronautTest
class PlaceTest {
    @Test
    fun testPlace(objectMapper: ObjectMapper) {
        val result = objectMapper.writeValueAsString(Place(Point.valueOf(50, 100), Point.valueOf(1, 2), Point.valueOf(3, 4)))
        val place = objectMapper.readValue(result, Place::class.java)
        Assertions.assertNotNull(place)
        Assertions.assertEquals(50, place.point.coords()[0])
        Assertions.assertEquals(100, place.point.coords()[1])
        Assertions.assertEquals(2, place.pointCustomSer.coords()[0])
        Assertions.assertEquals(1, place.pointCustomSer.coords()[1])
        Assertions.assertEquals(4, place.pointCustomDes.coords()[0])
        Assertions.assertEquals(3, place.pointCustomDes.coords()[1])
        Assertions.assertEquals("""{"point":[100,50],"pointCustomSer":[2,1],"pointCustomDes":[3,4]}""", result)
    }
}