package io.micronaut.serde.jackson.compiletime;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.SerdeableGenerated;
import org.jspecify.annotations.Nullable;

/**
 * Required components and aliases resolved at build time.
 */
@SerdeableGenerated
public record SourceGenRequiredComponentsRecord(
    @JsonProperty(required = true) String name,
    @JsonProperty(required = true) @Nullable String note,
    @JsonAlias("n") int count,
    String label
) {
}
