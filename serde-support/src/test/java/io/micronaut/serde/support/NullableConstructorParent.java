package io.micronaut.serde.support;

import org.jspecify.annotations.Nullable;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public final class NullableConstructorParent {
    @Nullable
    private NullableConstructorValue value;

    public @Nullable NullableConstructorValue getValue() {
        return value;
    }

    public void setValue(@Nullable NullableConstructorValue value) {
        this.value = value;
    }

    @Serdeable
    public record NullableConstructorValue(@Nullable String value) {
    }
}
