package io.micronaut.serde.jackson.compiletime;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.SerdeableGenerated;

import java.util.List;

/**
 * A type-level inclusion with component-level overrides.
 */
@SerdeableGenerated
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SourceGenIncludeOverridesRecord(
    String name,
    List<String> items,
    int count,
    @JsonInclude(JsonInclude.Include.ALWAYS) String always,
    @JsonInclude(JsonInclude.Include.NON_DEFAULT) int score
) {
}
