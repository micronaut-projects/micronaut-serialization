package io.micronaut.serde.jackson.tst;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.micronaut.core.annotation.Introspected;

import java.util.Map;

@Introspected
public record ClassificationAndStats<T extends StatsEntry>(
    ClassificationVars klassifisering,
    /** Ignore field to avoid double wrapping of values in resulting JSON */
    @JsonIgnore
    T stats
) {
    @JsonGetter("stats")
    Map<Aggregation, Integer> getValues() {
        return stats.getShouldNotAppearInJson();
    }
}
