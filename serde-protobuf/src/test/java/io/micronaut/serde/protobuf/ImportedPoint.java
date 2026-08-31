package io.micronaut.serde.protobuf;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.protobuf.annotation.ProtoField;

/**
 * Introspected but not {@code @Serdeable}, so it is bound through {@link SerdeImports} at runtime
 * instead of through a compile-time generated serde.
 */
@Introspected
public record ImportedPoint(@ProtoField(1) int x, @ProtoField(2) int y) {
}
