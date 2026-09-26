package example

import io.micronaut.serde.protobuf.ProtobufMapper
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Field numbers, not names, are the contract. As long as numbers are never reused, a service can
 * add fields without waiting for its readers to catch up.
 */
@MicronautTest(startApplication = false)
class SchemaEvolutionTest {

    @Inject
    lateinit var protobufMapper: ProtobufMapper

    // tag::forward[]
    @Test
    fun anOldReaderIgnoresFieldsItDoesNotKnow() {
        val current = SensorReadingTest.reading()

        val payload = protobufMapper.writeValueAsBytes(current)
        val asOldReader = protobufMapper.readValue(payload, LegacyReading::class.java)!!   // <1>

        assertEquals("weather-station-7", asOldReader.deviceId)
        assertEquals(-4250, asOldReader.temperatureMilliCelsius)
    }
    // end::forward[]

    // tag::backward[]
    @Test
    fun aNewReaderToleratesFieldsThatAreNotThereYet() {
        val old = LegacyReading("weather-station-7", 1787000000000L, -4250)

        val payload = protobufMapper.writeValueAsBytes(old)
        val asNewReader = protobufMapper.readValue(payload, SensorReading::class.java)!!   // <1>

        assertEquals(-4250, asNewReader.temperatureMilliCelsius)
        assertNull(asNewReader.location)
        assertNull(asNewReader.signalStrengths)
        assertFalse(asNewReader.batteryLow)
    }
    // end::backward[]

    @Test
    fun repeatedValuesSurviveTheRoundTrip() {
        val reading = SensorReadingTest.reading()
        val decoded = protobufMapper.readValue(
            protobufMapper.writeValueAsBytes(reading), SensorReading::class.java)!!
        assertEquals(listOf(-71, -68, -74), decoded.signalStrengths)
    }
}
