package example

import io.micronaut.json.JsonMapper
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@MicronautTest
class SerdeNullableTest {

    @Test
    fun testDefaultValue(objectMapper: JsonMapper) {
        val result = objectMapper.writeValueAsString(NullDto())
        val bean = objectMapper.readValue(result, NullDto::class.java)
        Assertions.assertEquals(null, bean.longField)
    }

    @Test
    fun testNonNullValue(objectMapper: JsonMapper) {
        val bean = objectMapper.readValue("{}", NonNullDto::class.java)
        Assertions.assertEquals(0, bean.longField)
    }

    @Test
    fun testNonNullValue2(objectMapper: JsonMapper) {
        val bean = objectMapper.readValue("{\"longField\":null}", NonNullDto::class.java)
        Assertions.assertEquals(0, bean.longField)
    }

    @Test
    fun testNullPropertyValue(objectMapper: JsonMapper) {
        val bean = objectMapper.readValue("{}", NullPropertyDto::class.java)
        Assertions.assertEquals(null, bean.longField)
    }

}
