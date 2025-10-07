package io.micronaut.serde.jackson.generic.scenario1;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ResourceDeleteResponse<T>(
    @JsonUnwrapped
    T object
) implements DeleteResponse<T> {
    @Override
    public V1Status status() {
        return null;
    }
}
