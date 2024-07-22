package example

import io.micronaut.context.annotation.Property
import io.micronaut.json.JsonMapper
import io.micronaut.serde.exceptions.SerdeException
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@Property(name = "micronaut.serde.deserialization.failOnNullForPrimitives", value = "true")
@MicronautTest
class SerdeNullableFailOnMissingTest {

    @Test
    fun testDefaultValue(objectMapper: JsonMapper) {
        val result = objectMapper.writeValueAsString(NullDto())
        val bean = objectMapper.readValue(result, NullDto::class.java)
        Assertions.assertEquals(null, bean.longField)
    }

    @Test
    fun testNonNullValue(objectMapper: JsonMapper) {
        val e = Assertions.assertThrows(SerdeException::class.java) {
            objectMapper.readValue("{}", NonNullDto::class.java)
        }
        Assertions.assertEquals(
            "Unable to deserialize type [example.NonNullDto]. Required constructor parameter [long longField] at index [0] is not present or is null in the supplied data",
            e.message
        )
    }

    @Test
    fun testNonNullValue2(objectMapper: JsonMapper) {
        val e = Assertions.assertThrows(SerdeException::class.java) {
            objectMapper.readValue("{\"longField\": null}", NonNullDto::class.java)
        }
        e.printStackTrace();
        Assertions.assertEquals(
            "Unable to deserialize type [example.NonNullDto]. Required constructor parameter [long longField] at index [0] is not present or is null in the supplied data",
            e.message
        )
    }

    @Test
    fun testNullPropertyValue(objectMapper: JsonMapper) {
        val bean = objectMapper.readValue("{}", NullPropertyDto::class.java)
        Assertions.assertEquals(null, bean.longField)
    }

}
