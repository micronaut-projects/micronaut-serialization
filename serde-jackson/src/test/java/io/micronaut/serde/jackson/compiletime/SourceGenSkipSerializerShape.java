package io.micronaut.serde.jackson.compiletime;

import io.micronaut.serde.annotation.SerdeableGenerated;

@SerdeableGenerated(skipSerializer = true)
public record SourceGenSkipSerializerShape(String name) {
}
