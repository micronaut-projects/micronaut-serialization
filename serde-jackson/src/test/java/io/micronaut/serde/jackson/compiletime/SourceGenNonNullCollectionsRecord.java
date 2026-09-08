package io.micronaut.serde.jackson.compiletime;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.serde.annotation.SerdeableGenerated;

import java.util.Collection;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;

@SerdeableGenerated
@Introspected
public record SourceGenNonNullCollectionsRecord(
    @NonNull List<String> list,
    @NonNull Collection<String> collection,
    @NonNull Set<String> set,
    @NonNull SortedSet<String> sortedSet,
    @NonNull Deque<String> deque,
    @NonNull LinkedList<String> linkedList,
    @NonNull Map<String, String> map,
    @NonNull SortedMap<String, String> sortedMap
) {
}
