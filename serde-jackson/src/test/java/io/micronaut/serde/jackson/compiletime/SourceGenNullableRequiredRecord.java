package io.micronaut.serde.jackson.compiletime;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.SerdeableGenerated;

/**
 * Required components that are declared nullable: an explicit null is accepted, a missing component
 * is still rejected.
 */
@SerdeableGenerated
public record SourceGenNullableRequiredRecord(
    @JsonProperty(required = true) @Nullable String name,
    @JsonProperty(required = true) @org.jspecify.annotations.Nullable String label,
    @JsonProperty(required = true) @Nullable Integer count,
    String note
) {
}
