package io.micronaut.serde.jackson.nullablenested;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record UnwrappedSetterInner(@Nullable String first, @Nullable String last) {
}
