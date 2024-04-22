package io.micronaut.serde.jackson.tst;

import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.core.annotation.Introspected;

@Introspected
public enum Aggregation {

    FIELD_1("SomeField1"),
    FIELD_2("SomeField2");

    private String fieldName;

    Aggregation(String fieldName) {
        this.fieldName = fieldName;
    }

    @JsonValue
    public String getFieldName() {
        return fieldName;
    }
}
