package example;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.protobuf.annotation.ProtoField;
import io.micronaut.serde.protobuf.annotation.ProtoType;

// tag::clazz[]
@Serdeable
public record Coordinates(
    @ProtoField(value = 1, type = ProtoType.SFIXED32) int latitudeMicros,   // <1>
    @ProtoField(value = 2, type = ProtoType.SFIXED32) int longitudeMicros
) {
}
// end::clazz[]
