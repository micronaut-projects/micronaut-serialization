package io.micronaut.serde;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record RecordBean(
    String foo,
    String bar
) {
}
