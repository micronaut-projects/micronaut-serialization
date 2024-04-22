package io.micronaut.serde.jackson.tst;

import java.util.Map;

public interface StatsEntry {
    Map<Aggregation, Integer> getShouldNotAppearInJson();
}
