package io.micronaut.serde.jackson.compiletime;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.micronaut.serde.annotation.SerdeableGenerated;

/**
 * An alphabetic property order is expanded into an explicit order during annotation processing.
 */
@SerdeableGenerated
@JsonPropertyOrder(alphabetic = true)
public record SourceGenAlphabeticOrderRecord(String charlie, int alpha, boolean bravo) {
}
