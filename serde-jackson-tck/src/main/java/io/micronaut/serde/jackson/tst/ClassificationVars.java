package io.micronaut.serde.jackson.tst;

import io.micronaut.core.annotation.Introspected;

@Introspected
public record ClassificationVars(
    String regionKode
) {
}
