package example;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.protobuf.annotation.ProtoField;
import io.micronaut.serde.protobuf.annotation.ProtoType;

// tag::clazz[]
@Serdeable
public record LegacyReading(
    @ProtoField(1) String deviceId,
    @ProtoField(2) long recordedAt,
    @ProtoField(value = 3, type = ProtoType.SINT32) int temperatureMilliCelsius
) {
}
// end::clazz[]
