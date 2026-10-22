package io.micronaut.serde.jackson.specific;

import io.micronaut.serde.annotation.SerdeableGenerated;

@SerdeableGenerated(required = false)
public record SecretHolder(Secret secret) {
}
