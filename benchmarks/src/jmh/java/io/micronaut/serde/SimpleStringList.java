package io.micronaut.serde;

import io.micronaut.core.annotation.Introspected;

import java.util.List;

@Introspected
public record SimpleStringList(List<String> strs) {
}
