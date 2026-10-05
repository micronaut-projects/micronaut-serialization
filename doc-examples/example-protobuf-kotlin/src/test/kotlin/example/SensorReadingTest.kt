package example

import io.micronaut.json.JsonMapper
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@MicronautTest(startApplication = false)
class SensorReadingTest {

    @Inject
    lateinit var codec: ReadingCodec

    @Inject
    lateinit var jsonMapper: JsonMapper

    companion object {
        fun reading() = SensorReading(
            "weather-station-7",
            1787000000000L,
            -4250,                                  // -4.25 °C
            listOf(-71, -68, -74),
            Coordinates(51507400, -127800),         // London, in microdegrees
            false
        )
    }

    // tag::roundTrip[]
    @Test
    fun readsBackWhatItWrote() {
        val reading = reading()

        val payload = codec.encode(reading)

        assertEquals(reading, codec.decode(payload))
    }
    // end::roundTrip[]

    // tag::size[]
    @Test
    fun isSubstantiallySmallerThanJson() {
        val reading = reading()

        val protobufBytes = codec.encode(reading).size
        val jsonBytes = jsonMapper.writeValueAsBytes(reading).size

        assertEquals(73, protobufBytes)                     // <1>
        assertTrue(protobufBytes * 2 < jsonBytes) {         // <2>
            "protobuf $protobufBytes bytes vs JSON $jsonBytes bytes"
        }
    }
    // end::size[]
}
