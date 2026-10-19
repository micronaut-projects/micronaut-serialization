package io.micronaut.serde.jackson.compiletime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.micronaut.serde.annotation.Serdeable;

/**
 * A type-level property order places the properties declared by methods among the components.
 */
@Serdeable
@JsonPropertyOrder({"computed", "name"})
public record SourceGenGetterMethodOrderRecord(String name) {

    @JsonProperty("computed")
    public String computed() {
        return name + "!";
    }
}
