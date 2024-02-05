package io.micronaut.serde.jackson.mixin;

public interface Message<T extends Request> {
    T payload();
}
