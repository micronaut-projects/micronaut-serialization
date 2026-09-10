package io.micronaut.serde.jackson.compiletime;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.SerdeableGenerated;

/**
 * Renamed record components are written and read with the name resolved at build time.
 */
@SerdeableGenerated
public record SourceGenRenamedPropertiesRecord(
    @JsonProperty("first_name") String firstName,
    @JsonProperty("n") int count,
    String email
) {
}
