package io.micronaut.serde.data;

import io.micronaut.core.annotation.Introspected;

@Introspected
public record IntArrayConstructor(int[] integers) {
}
