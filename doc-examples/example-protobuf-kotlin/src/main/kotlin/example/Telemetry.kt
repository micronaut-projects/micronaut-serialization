package example

import io.micronaut.serde.annotation.Serdeable
import io.micronaut.serde.protobuf.annotation.ProtoField
import io.micronaut.serde.protobuf.annotation.ProtoType

// tag::clazz[]
@Serdeable
data class Telemetry(
    val deviceId: String,                                                    // <1>
    val recordedAt: Long,
    @field:ProtoField(type = ProtoType.SINT32) val temperatureMilliCelsius: Int
)
// end::clazz[]
