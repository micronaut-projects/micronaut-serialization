package example;

import io.micronaut.serde.protobuf.ProtobufMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A message that names no positions is numbered by declaration order, and a property can still
 * choose its wire representation without choosing its number.
 */
@MicronautTest(startApplication = false)
class TelemetryTest {

    @Inject
    ProtobufMapper protobufMapper;

    @Test
    void isNumberedByDeclarationOrder() throws Exception {
        Telemetry telemetry = new Telemetry("weather-station-7", 1787000000000L, -4250);

        byte[] payload = protobufMapper.writeValueAsBytes(telemetry);

        assertEquals(telemetry, protobufMapper.readValue(payload, Telemetry.class));
        // the same bytes as declaring 1, 2 and 3 by hand
        assertArrayEquals(protobufMapper.writeValueAsBytes(
            new LegacyReading("weather-station-7", 1787000000000L, -4250)), payload);
    }
}
