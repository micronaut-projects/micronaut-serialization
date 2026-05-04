package io.micronaut.serde.data;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.SerdeableGenerated;

import java.util.List;

@Introspected
@SerdeableGenerated
public record InputConstructor(List<String> haystack, String needle) {
}
