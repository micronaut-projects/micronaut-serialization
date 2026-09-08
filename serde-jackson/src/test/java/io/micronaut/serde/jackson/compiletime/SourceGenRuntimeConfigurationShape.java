package io.micronaut.serde.jackson.compiletime;

import io.micronaut.serde.annotation.SerdeableGenerated;

/**
 * Camel-case property names and non-alphabetical declaration order: the generated serdes are used
 * unless a runtime naming strategy or alphabetical sorting is configured.
 */
@SerdeableGenerated
public record SourceGenRuntimeConfigurationShape(String lastName, String firstName, int zipCode) {
}
