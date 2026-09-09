package example;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.protobuf.annotation.ProtoField;
import io.micronaut.serde.protobuf.annotation.ProtoType;

/**
 * The same value under the two representations protobuf offers for a signed 32-bit integer.
 */
public final class Temperature {

    private Temperature() {
    }

    // tag::clazz[]
    @Serdeable
    public record AsInt32(@ProtoField(1) int milliCelsius) {
    }

    @Serdeable
    public record AsSint32(@ProtoField(value = 1, type = ProtoType.SINT32) int milliCelsius) {
    }
    // end::clazz[]
}
