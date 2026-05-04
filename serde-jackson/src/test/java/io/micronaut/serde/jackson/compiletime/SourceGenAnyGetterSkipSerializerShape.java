package io.micronaut.serde.jackson.compiletime;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import io.micronaut.serde.annotation.SerdeableGenerated;

import java.util.LinkedHashMap;
import java.util.Map;

@SerdeableGenerated(skipSerializer = true)
public class SourceGenAnyGetterSkipSerializerShape {
    private String name;
    private Map<String, Object> attributes = new LinkedHashMap<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @JsonAnyGetter
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }
}
