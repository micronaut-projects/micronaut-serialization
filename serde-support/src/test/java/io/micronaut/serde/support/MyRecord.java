package io.micronaut.serde.support;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record MyRecord(String message, boolean valid, Object additionalData) {
}
