package io.micronaut.serde.jackson.nonpublicsetter;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Jackson-only model compiled ahead of the in-memory compilation of the specs, which sees it as a
 * binary class.
 */
public final class LegacyJacksonContainer {
    @JsonProperty("values")
    protected Map<String, LegacyJacksonValue> values;
}
