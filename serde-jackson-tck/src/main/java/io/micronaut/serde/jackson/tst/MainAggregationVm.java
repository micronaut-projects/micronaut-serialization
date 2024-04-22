package io.micronaut.serde.jackson.tst;

import io.micronaut.core.annotation.Introspected;

import java.util.List;

@Introspected
public record MainAggregationVm(
    List<ClassificationAndStats<AfterCareStatsEntry>> afterCare
) {
}
