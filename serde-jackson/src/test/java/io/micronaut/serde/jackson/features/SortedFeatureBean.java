package io.micronaut.serde.jackson.features;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.Map;

@Serdeable
@JsonFormat(with = {
    JsonFormat.Feature.WRITE_SORTED_MAP_ENTRIES,
    JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY
})
public record SortedFeatureBean(Map<String, Integer> values, List<String> names) {
}
