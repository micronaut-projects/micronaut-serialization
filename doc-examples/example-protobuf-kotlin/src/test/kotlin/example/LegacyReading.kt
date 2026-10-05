package example

import io.micronaut.serde.annotation.Serdeable
import io.micronaut.serde.protobuf.annotation.ProtoField
import io.micronaut.serde.protobuf.annotation.ProtoType

// tag::clazz[]
@Serdeable
data class LegacyReading(
    @field:ProtoField(1) val deviceId: String,
    @field:ProtoField(2) val recordedAt: Long,
    @field:ProtoField(value = 3, type = ProtoType.SINT32) val temperatureMilliCelsius: Int
)
// end::clazz[]
