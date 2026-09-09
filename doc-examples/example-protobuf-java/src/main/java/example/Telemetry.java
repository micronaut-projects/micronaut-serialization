package example;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.protobuf.annotation.ProtoField;
import io.micronaut.serde.protobuf.annotation.ProtoType;

// tag::clazz[]
@Serdeable
public record Telemetry(
    String deviceId,                                                    // <1>
    long recordedAt,
    @ProtoField(type = ProtoType.SINT32) int temperatureMilliCelsius
) {
}
// end::clazz[]
