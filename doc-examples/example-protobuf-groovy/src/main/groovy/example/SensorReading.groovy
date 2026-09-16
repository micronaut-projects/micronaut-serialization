package example

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import io.micronaut.core.annotation.Nullable
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.serde.protobuf.annotation.ProtoField
import io.micronaut.serde.protobuf.annotation.ProtoType

// tag::clazz[]
@Serdeable
@EqualsAndHashCode
@ToString
class SensorReading {
    @ProtoField(1) final String deviceId
    @ProtoField(2) final long recordedAt
    @ProtoField(value = 3, type = ProtoType.SINT32) final int temperatureMilliCelsius   // <1>
    @ProtoField(4) final List<Integer> signalStrengths                                  // <2>
    @ProtoField(5) @Nullable final Coordinates location                                 // <3>
    @ProtoField(6) final boolean batteryLow

    SensorReading(String deviceId,
                  long recordedAt,
                  int temperatureMilliCelsius,
                  List<Integer> signalStrengths,
                  @Nullable Coordinates location,
                  boolean batteryLow) {
        this.deviceId = deviceId
        this.recordedAt = recordedAt
        this.temperatureMilliCelsius = temperatureMilliCelsius
        this.signalStrengths = signalStrengths
        this.location = location
        this.batteryLow = batteryLow
    }
}
// end::clazz[]
