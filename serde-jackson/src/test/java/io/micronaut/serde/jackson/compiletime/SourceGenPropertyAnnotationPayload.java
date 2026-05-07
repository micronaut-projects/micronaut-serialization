package io.micronaut.serde.jackson.compiletime;

import io.micronaut.serde.annotation.SerdeableGenerated;

@SerdeableGenerated
public class SourceGenPropertyAnnotationPayload {
    private String value;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
