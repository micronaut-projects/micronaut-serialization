package example

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.serde.protobuf.annotation.ProtoField
import io.micronaut.serde.protobuf.annotation.ProtoType

// tag::clazz[]
@Serdeable
@EqualsAndHashCode
@ToString
class Telemetry {
    final String deviceId                                                    // <1>
    final long recordedAt
    @ProtoField(type = ProtoType.SINT32) final int temperatureMilliCelsius

    Telemetry(String deviceId, long recordedAt, int temperatureMilliCelsius) {
        this.deviceId = deviceId
        this.recordedAt = recordedAt
        this.temperatureMilliCelsius = temperatureMilliCelsius
    }
}
// end::clazz[]
