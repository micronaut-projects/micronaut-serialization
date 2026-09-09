package io.micronaut.serde.protobuf;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.protobuf.annotation.ProtoField;

@Serdeable
public record Blob(@ProtoField(1) byte[] data) {
}
