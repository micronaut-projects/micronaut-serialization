package example

import io.micronaut.serde.protobuf.ProtobufMapper
import jakarta.inject.Singleton

// tag::clazz[]
@Singleton
class ReadingCodec {

    private final ProtobufMapper protobufMapper   // <1>

    ReadingCodec(ProtobufMapper protobufMapper) {
        this.protobufMapper = protobufMapper
    }

    byte[] encode(SensorReading reading) throws IOException {
        return protobufMapper.writeValueAsBytes(reading)
    }

    SensorReading decode(byte[] payload) throws IOException {
        return protobufMapper.readValue(payload, SensorReading)
    }
}
// end::clazz[]
