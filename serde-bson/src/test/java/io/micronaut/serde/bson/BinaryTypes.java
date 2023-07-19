package io.micronaut.serde.bson;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record BinaryTypes(
    byte[] uuid,
    byte[] normal,
    byte[] userDefined
) {
}
