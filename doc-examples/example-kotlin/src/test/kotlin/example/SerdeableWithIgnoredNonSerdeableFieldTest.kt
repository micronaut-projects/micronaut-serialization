package example

import com.fasterxml.jackson.annotation.JsonIgnore
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Test

@MicronautTest
class SerdeableWithIgnoredNonSerdeableFieldTest {

    @Test
    fun test(objectMapper: ObjectMapper) {
        val original = SerdeableWithIgnoredNonSerdeable(NonSerdeable("value"))

        val serialized = objectMapper.writeValueAsString(original)
        val deserialized = objectMapper.readValue(serialized, SerdeableWithIgnoredNonSerdeable::class.java)

        assert(deserialized == original.copy(ignoredNonSerdeable = null))
    }

}

@Serdeable
data class SerdeableWithIgnoredNonSerdeable(@field:JsonIgnore val ignoredNonSerdeable: NonSerdeable? = null)

data class NonSerdeable(val value: String)
