package example

import io.micronaut.serde.protobuf.ProtobufMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

/**
 * A message that names no positions is numbered by declaration order, and a property can still
 * choose its wire representation without choosing its number.
 */
@MicronautTest(startApplication = false)
class TelemetryTest extends Specification {

    @Inject
    ProtobufMapper protobufMapper

    void "is numbered by declaration order"() {
        given:
        Telemetry telemetry = new Telemetry("weather-station-7", 1787000000000L, -4250)

        when:
        byte[] payload = protobufMapper.writeValueAsBytes(telemetry)

        then:
        protobufMapper.readValue(payload, Telemetry) == telemetry
        // the same bytes as declaring 1, 2 and 3 by hand
        protobufMapper.writeValueAsBytes(
            new LegacyReading("weather-station-7", 1787000000000L, -4250)) == payload
    }
}
