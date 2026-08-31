package example;

import io.micronaut.serde.protobuf.ProtobufMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Field numbers, not names, are the contract. As long as numbers are never reused, a service can
 * add fields without waiting for its readers to catch up.
 */
@MicronautTest(startApplication = false)
class SchemaEvolutionTest {

    @Inject
    ProtobufMapper protobufMapper;

    // tag::forward[]
    @Test
    void anOldReaderIgnoresFieldsItDoesNotKnow() throws Exception {
        SensorReading current = SensorReadingTest.reading();

        byte[] payload = protobufMapper.writeValueAsBytes(current);
        LegacyReading asOldReader = protobufMapper.readValue(payload, LegacyReading.class);   // <1>

        assertEquals("weather-station-7", asOldReader.deviceId());
        assertEquals(-4250, asOldReader.temperatureMilliCelsius());
    }
    // end::forward[]

    // tag::backward[]
    @Test
    void aNewReaderToleratesFieldsThatAreNotThereYet() throws Exception {
        LegacyReading old = new LegacyReading("weather-station-7", 1787000000000L, -4250);

        byte[] payload = protobufMapper.writeValueAsBytes(old);
        SensorReading asNewReader = protobufMapper.readValue(payload, SensorReading.class);   // <1>

        assertEquals(-4250, asNewReader.temperatureMilliCelsius());
        assertNull(asNewReader.location());
        assertNull(asNewReader.signalStrengths());
        assertFalse(asNewReader.batteryLow());
    }
    // end::backward[]

    @Test
    void repeatedValuesSurviveTheRoundTrip() throws Exception {
        SensorReading reading = SensorReadingTest.reading();
        SensorReading decoded = protobufMapper.readValue(
            protobufMapper.writeValueAsBytes(reading), SensorReading.class);
        assertEquals(List.of(-71, -68, -74), decoded.signalStrengths());
    }
}
