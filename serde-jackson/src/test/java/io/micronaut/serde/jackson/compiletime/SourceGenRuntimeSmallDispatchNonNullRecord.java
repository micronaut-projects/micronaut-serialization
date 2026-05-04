package io.micronaut.serde.jackson.compiletime;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.serde.annotation.SerdeableGenerated;

@SerdeableGenerated(skipDeserializer = true)
@Introspected
public record SourceGenRuntimeSmallDispatchNonNullRecord(@NonNull String a, int b, boolean c) {
}
