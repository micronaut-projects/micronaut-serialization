package io.micronaut.serde.jackson.compiletime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.serde.annotation.Serdeable;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Serdeable
public class SourceGenPropertyAnnotationHolder {
    @JsonFormat(shape = JsonFormat.Shape.OBJECT)
    private SourceGenPropertyAnnotationPayload formatted;
    @JsonUnwrapped
    private SourceGenPropertyAnnotationPayload unwrapped;
    private SourceGenPropertyAnnotationPayload included;

    public SourceGenPropertyAnnotationPayload getFormatted() {
        return formatted;
    }

    public void setFormatted(SourceGenPropertyAnnotationPayload formatted) {
        this.formatted = formatted;
    }

    public SourceGenPropertyAnnotationPayload getUnwrapped() {
        return unwrapped;
    }

    public void setUnwrapped(SourceGenPropertyAnnotationPayload unwrapped) {
        this.unwrapped = unwrapped;
    }

    @JsonInclude(NON_NULL)
    public SourceGenPropertyAnnotationPayload getIncluded() {
        return included;
    }

    public void setIncluded(SourceGenPropertyAnnotationPayload included) {
        this.included = included;
    }
}
