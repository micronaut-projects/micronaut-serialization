package io.micronaut.serde.jackson.compiletime;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/**
 * Methods of a record that are not component accessors are written when they declare a property.
 */
@Serdeable
public record SourceGenGetterMethodRecord(String name, int count) {

    @JsonProperty("computed")
    public String computed() {
        return name + "!";
    }

    @JsonProperty
    public int doubled() {
        return count * 2;
    }

    @JsonGetter("tags")
    public List<String> tags() {
        return List.of(name, String.valueOf(count));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("missing")
    public String missing() {
        return null;
    }
}
