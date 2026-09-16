package example

import io.micronaut.core.type.Argument
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.exceptions.SerdeException
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@MicronautTest
class StrictBuilderExampleTest {

    @Test
    fun testDefaultValueIsAppliedForAMissingProperty(objectMapper: ObjectMapper) {
        val request = objectMapper.readValue(
            "{\"service\":\"checkout\"}",
            Argument.of(ReleaseRequest::class.java)
        )!!

        assertEquals("checkout", request.service)
        assertEquals("platform", request.owner)
        assertNull(request.notes)
    }

    @Test
    fun testMissingRequiredPropertyIsRejected(objectMapper: ObjectMapper) {
        val e = assertThrows(SerdeException::class.java) {
            objectMapper.readValue(
                "{\"owner\":\"growth\"}",
                Argument.of(ReleaseRequest::class.java)
            )
        }

        assertTrue(e.message!!.contains("Required property"))
    }
}
