package example

import io.micronaut.serde.protobuf.ProtobufMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

/**
 * Field numbers, not names, are the contract. As long as numbers are never reused, a service can
 * add fields without waiting for its readers to catch up.
 */
@MicronautTest(startApplication = false)
class SchemaEvolutionTest extends Specification {

    @Inject
    ProtobufMapper protobufMapper

    // tag::forward[]
    void "an old reader ignores fields it does not know"() {
        given:
        SensorReading current = SensorReadingTest.reading()

        when:
        byte[] payload = protobufMapper.writeValueAsBytes(current)
        LegacyReading asOldReader = protobufMapper.readValue(payload, LegacyReading)   // <1>

        then:
        asOldReader.deviceId == "weather-station-7"
        asOldReader.temperatureMilliCelsius == -4250
    }
    // end::forward[]

    // tag::backward[]
    void "a new reader tolerates fields that are not there yet"() {
        given:
        LegacyReading old = new LegacyReading("weather-station-7", 1787000000000L, -4250)

        when:
        byte[] payload = protobufMapper.writeValueAsBytes(old)
        SensorReading asNewReader = protobufMapper.readValue(payload, SensorReading)   // <1>

        then:
        asNewReader.temperatureMilliCelsius == -4250
        asNewReader.location == null
        asNewReader.signalStrengths == null
        !asNewReader.batteryLow
    }
    // end::backward[]

    void "repeated values survive the round trip"() {
        given:
        SensorReading reading = SensorReadingTest.reading()

        when:
        SensorReading decoded = protobufMapper.readValue(
            protobufMapper.writeValueAsBytes(reading), SensorReading)

        then:
        decoded.signalStrengths == List.of(-71, -68, -74)
    }
}
