package io.micronaut.serde.support;

import io.micronaut.http.hateoas.Resource;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.Map;

@Serdeable
public record ResourcesByRel(Map<String, List<Resource>> resources) {
}
