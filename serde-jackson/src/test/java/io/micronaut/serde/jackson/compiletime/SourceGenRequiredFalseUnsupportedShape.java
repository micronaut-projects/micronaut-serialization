package io.micronaut.serde.jackson.compiletime;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.serde.annotation.SerdeableGenerated;

/**
 * An unwrapped property is not supported by the generated serdes; {@code required = false} lets the
 * type fall back to the runtime serdes without failing compilation.
 */
@SerdeableGenerated(required = false)
public class SourceGenRequiredFalseUnsupportedShape {
    @JsonUnwrapped
    private SourceGenGeneratedShape nested;

    public SourceGenGeneratedShape getNested() {
        return nested;
    }

    public void setNested(SourceGenGeneratedShape nested) {
        this.nested = nested;
    }
}
