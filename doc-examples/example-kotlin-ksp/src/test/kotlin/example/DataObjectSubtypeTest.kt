package example

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

@MicronautTest
class DataObjectSubtypeTest {

    @Test
    fun testDataObjectSubtypeRoundTrip(objectMapper: ObjectMapper) {
        val json = objectMapper.writeValueAsString(Shape.Point)
        assertEquals("""{"type":"point"}""", json)

        val shape = objectMapper.readValue(json, Shape::class.java)
        assertSame(Shape.Point, shape)
    }

    @Test
    fun testDeserializeDataObjectSubtype(objectMapper: ObjectMapper) {
        val shape = objectMapper.readValue("""{"type":"point"}""", Shape::class.java)
        assertSame(Shape.Point, shape)
    }

    @Test
    fun testDataClassSubtypeRoundTrip(objectMapper: ObjectMapper) {
        val json = objectMapper.writeValueAsString(Shape.Circle(2.0))
        assertEquals("""{"type":"circle","radius":2.0}""", json)

        val shape = objectMapper.readValue(json, Shape::class.java)
        assertEquals(Shape.Circle(2.0), shape)
    }

    @Serdeable
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes(
        JsonSubTypes.Type(value = Shape.Point::class, name = "point"),
        JsonSubTypes.Type(value = Shape.Circle::class, name = "circle")
    )
    sealed interface Shape {
        @Serdeable
        data object Point : Shape

        @Serdeable
        data class Circle(val radius: Double) : Shape
    }
}
