package io.micronaut.serde.jackson.mixin;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record FooMessage<T extends Request>(
    T payload
) implements Message<T> { }
