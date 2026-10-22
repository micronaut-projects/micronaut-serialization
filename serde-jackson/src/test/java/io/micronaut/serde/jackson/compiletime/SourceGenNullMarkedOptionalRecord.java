package io.micronaut.serde.jackson.compiletime;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.SerdeableGenerated;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

@NullMarked
@SerdeableGenerated
@Introspected
public record SourceGenNullMarkedOptionalRecord(
    String name,
    Optional<String> nickname
) {
}
