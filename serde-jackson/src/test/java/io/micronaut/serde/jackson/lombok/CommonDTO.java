package io.micronaut.serde.jackson.lombok;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Introspected
@Serdeable.Deserializable
public record CommonDTO(
    @JsonProperty("common_field") @NotBlank String commonField,
    @JsonProperty("common_field2") @NotNull Integer commonField2) {
}

