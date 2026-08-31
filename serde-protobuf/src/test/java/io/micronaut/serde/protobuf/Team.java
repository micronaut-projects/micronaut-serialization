package io.micronaut.serde.protobuf;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.protobuf.annotation.ProtoField;

import java.util.List;

@Serdeable
public record Team(@ProtoField(1) String name, @ProtoField(2) List<Address> members) {
}
