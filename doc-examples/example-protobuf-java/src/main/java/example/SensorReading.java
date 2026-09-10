package example;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.protobuf.annotation.ProtoField;
import io.micronaut.serde.protobuf.annotation.ProtoType;

import java.util.List;

// tag::clazz[]
@Serdeable
public record SensorReading(
    @ProtoField(1) String deviceId,
    @ProtoField(2) long recordedAt,
    @ProtoField(value = 3, type = ProtoType.SINT32) int temperatureMilliCelsius,   // <1>
    @ProtoField(4) List<Integer> signalStrengths,                                  // <2>
    @ProtoField(5) @Nullable Coordinates location,                                 // <3>
    @ProtoField(6) boolean batteryLow
) {
}
// end::clazz[]
