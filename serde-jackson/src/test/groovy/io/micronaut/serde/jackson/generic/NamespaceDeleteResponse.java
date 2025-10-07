package io.micronaut.serde.jackson.generic;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record NamespaceDeleteResponse(
    @JsonUnwrapped
    V1Namespace namespace
) implements DeleteResponse<V1Namespace> {
    @Override
    public V1Namespace object() {
        return namespace;
    }

    @Override
    public V1Status status() {
        return null;
    }
}
