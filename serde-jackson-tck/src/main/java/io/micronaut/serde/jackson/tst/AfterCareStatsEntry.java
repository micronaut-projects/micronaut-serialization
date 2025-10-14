/*
 * Copyright 2017-2024 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
