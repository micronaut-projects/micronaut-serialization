package io.micronaut.serde.jackson.specific;

import io.micronaut.serde.annotation.SerdeableGenerated;
import io.micronaut.serde.jackson.compiletime.SourceGenGeneratedShape;

/**
 * A generated serde with a property bound to the context of the document, created before another generated property.
 */
@SerdeableGenerated(required = false)
public record SecretHolderOwner(SecretHolder holder, SourceGenGeneratedShape shape) {
}
