package io.micronaut.serde.protobuf;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.protobuf.annotation.ProtoField;
import io.micronaut.serde.protobuf.annotation.ProtoType;

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
    record DerivedClashesWithExplicit(String first, @ProtoField(1) String second) {
    }

    @Serdeable
    record ReservedNumber(@ProtoField(19001) String reserved) {
    }

    @Serdeable
    record NumberOutOfRange(@ProtoField(0) String zero) {
    }

    @Serdeable
    record StringWithNumericType(@ProtoField(value = 1, type = ProtoType.FIXED32) String value) {
    }

    @Serdeable
    record LongWithNarrowType(@ProtoField(value = 1, type = ProtoType.SFIXED32) long value) {
    }

    @Serdeable
    record UntypedProperty(@ProtoField(1) Object anything) {
    }
}
