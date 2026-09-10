package io.micronaut.serde.protobuf;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.protobuf.annotation.ProtoField;

/**
 * Self-nesting, so a payload can be built whose every level is nearly as large as its parent.
 */
@Serdeable
public record Recursive(@ProtoField(1) @Nullable String name, @ProtoField(2) @Nullable Recursive child) {
}
