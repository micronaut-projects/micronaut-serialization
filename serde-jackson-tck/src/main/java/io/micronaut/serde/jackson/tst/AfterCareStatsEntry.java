package io.micronaut.serde.jackson.tst;

import io.micronaut.core.annotation.Introspected;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Introspected
public record AfterCareStatsEntry() implements StatsEntry {

    static List<Aggregation> AFTERCARE_AGGREGATIONS = List.of(
        Aggregation.FIELD_1,
        Aggregation.FIELD_2
    );

    @Override
    public Map<Aggregation, Integer> getShouldNotAppearInJson() {
        return AFTERCARE_AGGREGATIONS.stream().collect(Collectors.toMap(it -> it, it -> 0, (x, y) -> y, LinkedHashMap::new));
    }
}
