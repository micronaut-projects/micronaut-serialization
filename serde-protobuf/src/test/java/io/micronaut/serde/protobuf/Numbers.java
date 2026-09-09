package io.micronaut.serde.protobuf;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.protobuf.annotation.ProtoField;
import io.micronaut.serde.protobuf.annotation.ProtoType;

@Serdeable
public record Numbers(
    @ProtoField(value = 1, type = ProtoType.FIXED32) int fixedWidth,
    @ProtoField(value = 2, type = ProtoType.SFIXED64) long signedFixedWidth,
    @ProtoField(value = 3, type = ProtoType.UINT32) int unsigned,
    @ProtoField(value = 4, type = ProtoType.SINT64) long zigZag,
    @ProtoField(5) float ratio
) {
}
