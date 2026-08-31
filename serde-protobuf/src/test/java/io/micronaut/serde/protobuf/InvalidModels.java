package io.micronaut.serde.protobuf;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.protobuf.annotation.ProtoField;

/**
 * Models whose field numbering is invalid, used to check that the schema is rejected with a
 * message that says what is wrong.
 */
final class InvalidModels {

    private InvalidModels() {
    }

    @Serdeable
    record DuplicateNumbers(@ProtoField(1) String first, @ProtoField(1) String second) {
    }

    @Serdeable
    record MissingNumber(@ProtoField(1) String named, String unnamed) {
    }

    @Serdeable
    record ReservedNumber(@ProtoField(19001) String reserved) {
    }

    @Serdeable
    record NumberOutOfRange(@ProtoField(0) String zero) {
    }

    @Serdeable
    record UntypedProperty(@ProtoField(1) Object anything) {
    }
}
