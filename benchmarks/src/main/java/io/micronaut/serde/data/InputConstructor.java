package io.micronaut.serde.data;

import io.micronaut.core.annotation.Introspected;

import java.util.List;

@Introspected
public record InputConstructor(List<String> haystack, String needle) {
}
