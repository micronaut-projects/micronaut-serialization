package io.micronaut.serde.jackson.compiletime;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/**
 * Methods of a bean that are not property accessors are written when they declare a property.
 */
@Serdeable
public class SourceGenGetterMethodBean {

    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @JsonProperty("display")
    public String display() {
        return "<" + name + ">";
    }

    @JsonGetter("letters")
    public List<String> letters() {
        return name == null ? List.of() : List.of(name.split(""));
    }
}
