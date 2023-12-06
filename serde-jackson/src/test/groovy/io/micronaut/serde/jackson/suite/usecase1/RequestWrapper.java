package io.micronaut.serde.jackson.suite.usecase1;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record RequestWrapper(
        Request request
) {
}
