package io.micronaut.serde.support;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.micronaut.http.hateoas.GenericResource;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record IgnoringResourceHolder(@JsonIgnoreProperties({"_links", "secret"}) GenericResource resource) {
}
