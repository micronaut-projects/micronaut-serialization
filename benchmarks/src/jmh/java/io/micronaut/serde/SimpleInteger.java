package io.micronaut.serde;

import io.micronaut.core.annotation.Introspected;

@Introspected
public record SimpleInteger(Integer integer) {
}
