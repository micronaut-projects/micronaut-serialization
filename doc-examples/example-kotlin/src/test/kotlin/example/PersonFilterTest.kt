package example

import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.IOException

@MicronautTest
class PersonFilterTest {

    @Test
    fun testWritePersonWithoutPreferredName(objectMapper: ObjectMapper) {
        val result = objectMapper.writeValueAsString(Person("Adam", null))
        Assertions.assertEquals("{\"name\":\"Adam\"}", result)
    }

    @Test
    fun testWritePersonWithPreferredName(objectMapper: ObjectMapper) {
        val result = objectMapper.writeValueAsString(Person("Adam", "Ad"))
        Assertions.assertEquals("{\"preferredName\":\"Ad\"}", result)
    }
}
