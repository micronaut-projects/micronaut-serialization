package example

import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@MicronautTest
class DefaultsTest {
    @Test
    fun test(objectMapper: ObjectMapper) {
        val result = objectMapper.readValue("""{"required": "some value"}""", Params::class.java)
        Assertions.assertEquals("some value", result.required)
        Assertions.assertEquals("default", result.stringDefault)
        Assertions.assertEquals(true, result.boolDefault)
        Assertions.assertEquals(5, result.longDefault)
    }
}
