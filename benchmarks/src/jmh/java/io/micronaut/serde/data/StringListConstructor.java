package io.micronaut.serde.data;

import io.micronaut.core.annotation.Introspected;

import java.util.List;

@Introspected
public record StringListConstructor(List<String> strs) {
}
