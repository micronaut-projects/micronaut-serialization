package io.micronaut.serde.protobuf;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.protobuf.annotation.ProtoField;

/**
 * The same shape as {@link ImportedPoint}, but {@code @Serdeable} so that it is bound through a
 * compile-time generated serde.
 */
@Serdeable
public record GeneratedPoint(@ProtoField(1) int x, @ProtoField(2) int y) {
}
