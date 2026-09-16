package example

import io.micronaut.json.JsonMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(startApplication = false)
class SensorReadingTest extends Specification {

    @Inject
    ReadingCodec codec

    @Inject
    JsonMapper jsonMapper

    static SensorReading reading() {
        return new SensorReading(
            "weather-station-7",
            1787000000000L,
            -4250,                                  // -4.25 °C
            List.of(-71, -68, -74),
            new Coordinates(51507400, -127800),     // London, in microdegrees
            false
        )
    }

    // tag::roundTrip[]
    void "reads back what it wrote"() {
        given:
        SensorReading reading = reading()

        when:
        byte[] payload = codec.encode(reading)

        then:
        codec.decode(payload) == reading
    }
    // end::roundTrip[]

    // tag::size[]
    void "is substantially smaller than JSON"() {
        given:
        SensorReading reading = reading()

        when:
        int protobufBytes = codec.encode(reading).length
        int jsonBytes = jsonMapper.writeValueAsBytes(reading).length

        then:
        protobufBytes == 73                                 // <1>
        protobufBytes * 2 < jsonBytes                       // <2>
    }
    // end::size[]
}
