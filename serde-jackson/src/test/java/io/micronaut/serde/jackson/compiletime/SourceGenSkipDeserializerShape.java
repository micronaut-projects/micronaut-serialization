package io.micronaut.serde.jackson.compiletime;

import io.micronaut.serde.annotation.SerdeableGenerated;

@SerdeableGenerated(skipDeserializer = true)
public record SourceGenSkipDeserializerShape(String name) {
}
