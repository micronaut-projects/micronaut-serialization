package example

import io.micronaut.serde.protobuf.ProtobufMapper
import jakarta.inject.Singleton

// tag::clazz[]
@Singleton
class ReadingCodec(
    private val protobufMapper: ProtobufMapper   // <1>
) {

    fun encode(reading: SensorReading): ByteArray =
        protobufMapper.writeValueAsBytes(reading)

    fun decode(payload: ByteArray): SensorReading =
        protobufMapper.readValue(payload, SensorReading::class.java)!!
}
// end::clazz[]
