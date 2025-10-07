package io.micronaut.serde.jackson.generic;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record StatusDeleteResponse<T>(
    @JsonUnwrapped
    V1Status status
) implements DeleteResponse<T> {
    @Override
    public T object() {
        return null;
    }
}
