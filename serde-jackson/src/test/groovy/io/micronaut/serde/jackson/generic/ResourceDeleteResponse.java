package io.micronaut.serde.jackson.generic;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ResourceDeleteResponse<T>(
    T object
) implements DeleteResponse<T> {
    @Override
    public V1Status status() {
        return null;
    }
}
