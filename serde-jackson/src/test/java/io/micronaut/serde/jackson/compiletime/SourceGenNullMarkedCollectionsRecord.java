package io.micronaut.serde.jackson.compiletime;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.SerdeableGenerated;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Map;
import java.util.Set;

@NullMarked
@SerdeableGenerated
@Introspected
public record SourceGenNullMarkedCollectionsRecord(
    String name,
    List<String> list,
    Set<String> set,
    Map<String, String> map
) {
}
