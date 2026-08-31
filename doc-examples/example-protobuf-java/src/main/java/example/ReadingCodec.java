package example;

import io.micronaut.serde.protobuf.ProtobufMapper;
import jakarta.inject.Singleton;

import java.io.IOException;

// tag::clazz[]
@Singleton
public class ReadingCodec {

    private final ProtobufMapper protobufMapper;   // <1>

    public ReadingCodec(ProtobufMapper protobufMapper) {
        this.protobufMapper = protobufMapper;
    }

    public byte[] encode(SensorReading reading) throws IOException {
        return protobufMapper.writeValueAsBytes(reading);
    }

    public SensorReading decode(byte[] payload) throws IOException {
        return protobufMapper.readValue(payload, SensorReading.class);
    }
}
// end::clazz[]
