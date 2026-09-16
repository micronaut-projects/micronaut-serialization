package example

import io.micronaut.serde.annotation.Serdeable
import io.micronaut.serde.protobuf.annotation.ProtoField
import io.micronaut.serde.protobuf.annotation.ProtoType

/**
 * The same value under the two representations protobuf offers for a signed 32-bit integer.
 */
final class Temperature {

    private Temperature() {
    }

    // tag::clazz[]
    @Serdeable
    static class AsInt32 {
        @ProtoField(1) final int milliCelsius

        AsInt32(int milliCelsius) {
            this.milliCelsius = milliCelsius
        }
    }

    @Serdeable
    static class AsSint32 {
        @ProtoField(value = 1, type = ProtoType.SINT32) final int milliCelsius

        AsSint32(int milliCelsius) {
            this.milliCelsius = milliCelsius
        }
    }
    // end::clazz[]
}
