package io.micronaut.serde.support;

import io.micronaut.http.hateoas.Resource;
import io.micronaut.serde.annotation.Serdeable;
import org.jspecify.annotations.Nullable;

@Serdeable
public record ResourceHolder(String name, @Nullable Resource resource) {
}
