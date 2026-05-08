package io.micronaut.serde.jackson.compiletime;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.SerdeableGenerated;

@SerdeableGenerated
public record SourceGenRuntimeConstructorDefaults(
    String name,
    boolean active,
    int count,
    @Nullable String nullableName,
    @Nullable Boolean nullableActive
) {
}
