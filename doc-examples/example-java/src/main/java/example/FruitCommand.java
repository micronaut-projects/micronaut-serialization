package example;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotEmpty;

@Serdeable
record FruitCommand(
    @NotEmpty String name, @Nullable String description
) {}
