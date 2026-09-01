package example;

import io.micronaut.json.JsonMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(startApplication = false)
class SensorReadingTest {

    @Inject
    ReadingCodec codec;

    @Inject
    JsonMapper jsonMapper;

    static SensorReading reading() {
        return new SensorReading(
            "weather-station-7",
            1787000000000L,
            -4250,                                  // -4.25 °C
            List.of(-71, -68, -74),
            new Coordinates(51507400, -127800),     // London, in microdegrees
            false
        );
    }

    // tag::roundTrip[]
    @Test
    void readsBackWhatItWrote() throws Exception {
        SensorReading reading = reading();

        byte[] payload = codec.encode(reading);

        assertEquals(reading, codec.decode(payload));
    }
    // end::roundTrip[]

    // tag::size[]
    @Test
    void isSubstantiallySmallerThanJson() throws Exception {
        SensorReading reading = reading();

        int protobufBytes = codec.encode(reading).length;
        int jsonBytes = jsonMapper.writeValueAsBytes(reading).length;

        assertEquals(73, protobufBytes);                    // <1>
        assertTrue(protobufBytes * 2 < jsonBytes,           // <2>
            () -> "protobuf " + protobufBytes + " bytes vs JSON " + jsonBytes + " bytes");
    }
    // end::size[]
}
