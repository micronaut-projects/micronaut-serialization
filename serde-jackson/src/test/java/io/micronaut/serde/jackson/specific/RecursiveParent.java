package io.micronaut.serde.jackson.specific;

import io.micronaut.serde.annotation.SerdeableGenerated;

/**
 * A generated serde that refers back to itself through another generated serde.
 */
@SerdeableGenerated(required = false)
public record RecursiveParent(String name, RecursiveChild child) {
}
