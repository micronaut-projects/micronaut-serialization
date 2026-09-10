package io.micronaut.serde.protobuf;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.protobuf.annotation.ProtoField;

@Serdeable
public record Address(@ProtoField(1) String street, @ProtoField(2) String city) {
}
