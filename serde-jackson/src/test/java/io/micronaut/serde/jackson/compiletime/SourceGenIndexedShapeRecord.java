package io.micronaut.serde.jackson.compiletime;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.SerdeableGenerated;

import java.util.List;

@SerdeableGenerated
@Introspected
public record SourceGenIndexedShapeRecord(String value, List<String> tags) {
}
