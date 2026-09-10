package io.micronaut.serde.jackson.compiletime;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.config.naming.SnakeCaseStrategy;

/**
 * A naming strategy resolved during annotation processing is baked into the generated serdes.
 */
@Serdeable(naming = SnakeCaseStrategy.class)
public record SourceGenSnakeCaseNamingRecord(String firstName, String lastName, int zipCode) {
}
