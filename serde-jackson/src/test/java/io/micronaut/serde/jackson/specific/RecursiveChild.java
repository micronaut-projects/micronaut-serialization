package io.micronaut.serde.jackson.specific;

import io.micronaut.serde.annotation.SerdeableGenerated;

/**
 * The generated serde of the property that refers back to {@link RecursiveParent}.
 */
@SerdeableGenerated(required = false)
public record RecursiveChild(String name, RecursiveParent parent) {
}
