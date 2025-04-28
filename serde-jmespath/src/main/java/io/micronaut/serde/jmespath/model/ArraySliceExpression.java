package io.micronaut.serde.jmespath.model;

import jakarta.annotation.Nullable;

public record ArraySliceExpression(@Nullable Long from, @Nullable Long to, @Nullable Long step) implements ArrayExpression {
}
