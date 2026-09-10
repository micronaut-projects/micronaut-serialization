package io.micronaut.serde.protobuf;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.protobuf.annotation.ProtoField;
import io.micronaut.serde.protobuf.annotation.ProtoType;

import java.util.List;

@Serdeable
public record Person(
    @ProtoField(1) String name,
    @ProtoField(2) int age,
    @ProtoField(3) @Nullable Address address,
    @ProtoField(4) List<String> nicknames,
    @ProtoField(5) List<Integer> scores,
    @ProtoField(value = 6, type = ProtoType.SINT32) int balance,
    @ProtoField(7) boolean active,
    @ProtoField(8) double ratio
) {
}
