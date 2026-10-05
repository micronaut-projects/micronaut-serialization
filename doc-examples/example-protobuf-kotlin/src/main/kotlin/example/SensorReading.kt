package example

import io.micronaut.serde.annotation.Serdeable
import io.micronaut.serde.protobuf.annotation.ProtoField
import io.micronaut.serde.protobuf.annotation.ProtoType

// tag::clazz[]
@Serdeable
data class SensorReading(
    @field:ProtoField(1) val deviceId: String,
    @field:ProtoField(2) val recordedAt: Long,
    @field:ProtoField(value = 3, type = ProtoType.SINT32) val temperatureMilliCelsius: Int,   // <1>
    @field:ProtoField(4) val signalStrengths: List<Int>?,                                     // <2>
    @field:ProtoField(5) val location: Coordinates?,                                          // <3>
    @field:ProtoField(6) val batteryLow: Boolean
)
// end::clazz[]
