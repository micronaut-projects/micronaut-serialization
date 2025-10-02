package io.micronaut.serde.jackson.generic;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record DeleteResponse<T>(
    @Nullable T object,
    @Nullable V1Status status
) {
}
