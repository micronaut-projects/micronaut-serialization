package io.micronaut.serde.protobuf;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.protobuf.annotation.ProtoField;

/**
 * A narrower view of the same message: reading a full {@code Person} payload into this type
 * exercises the forward-compatibility path, where fields the reader does not know are skipped.
 */
@Serdeable
public record NameAndAge(@ProtoField(1) String name, @ProtoField(2) int age) {
}
