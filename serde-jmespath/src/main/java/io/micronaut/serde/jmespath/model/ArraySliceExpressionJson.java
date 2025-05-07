package io.micronaut.serde.jmespath.model;

import jakarta.annotation.Nullable;

public record ArraySliceExpressionJson(@Nullable Long from, @Nullable Long to, @Nullable Long step) implements ArrayExpressionJson {
}
