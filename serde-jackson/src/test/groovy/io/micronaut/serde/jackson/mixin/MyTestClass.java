package io.micronaut.serde.jackson.mixin;

public record MyTestClass(
    String name
) implements Request { }
