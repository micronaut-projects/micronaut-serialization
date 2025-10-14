package io.micronaut.serde.jackson.lombok;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
@Introspected
@Serdeable.Deserializable
public record MyDTO(
    @JsonProperty("id") @NotNull Integer id,
    @JsonUnwrapped @NotNull @Valid CommonDTO commonDTO) {
}
