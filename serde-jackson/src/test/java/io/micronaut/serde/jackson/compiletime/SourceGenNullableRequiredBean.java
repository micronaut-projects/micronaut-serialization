package io.micronaut.serde.jackson.compiletime;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.SerdeableGenerated;

/**
 * Required properties that are declared nullable: an explicit null is accepted, a missing property
 * is still rejected.
 */
@SerdeableGenerated
public class SourceGenNullableRequiredBean {

    @JsonProperty(required = true)
    @Nullable
    private String name;

    @JsonProperty(required = true)
    private @org.jspecify.annotations.Nullable String label;

    @JsonProperty(required = true)
    @Nullable
    private Integer count;

    private String note;

    @Nullable
    public String getName() {
        return name;
    }

    public void setName(@Nullable String name) {
        this.name = name;
    }

    public @org.jspecify.annotations.Nullable String getLabel() {
        return label;
    }

    public void setLabel(@org.jspecify.annotations.Nullable String label) {
        this.label = label;
    }

    @Nullable
    public Integer getCount() {
        return count;
    }

    public void setCount(@Nullable Integer count) {
        this.count = count;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
