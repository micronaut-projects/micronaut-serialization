package io.micronaut.serde.jackson.compiletime;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.SerdeableGenerated;

@SerdeableGenerated(required = false)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SourceGenRequiredFalseUnsupportedShape {
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
