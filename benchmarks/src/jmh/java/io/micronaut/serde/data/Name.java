package io.micronaut.serde.data;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.SerdeableGenerated;

@Introspected
@SerdeableGenerated
public record Name(String firstName, String lastName) {
}
