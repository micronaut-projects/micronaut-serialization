package io.micronaut.serde.jackson.suite.usecase1;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public final class RequestType1 extends Request {
    public static final String JSON_OUTPUT_SETTINGS = "output_settings";

    OutputSettings settings;

    @JsonCreator
    public RequestType1(
            @JsonProperty(JSON_VALUE1) String value1,
            @JsonProperty(JSON_VALUE2) String value2) {
        super(value1, value2);
    }

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public RequestType1(
            @JsonProperty(JSON_VALUE1) String value1,
            @JsonProperty(JSON_VALUE2) String value2,
            OutputSettings settings
    ) {
        this(value1, value2);
        this.settings = settings;
    }

    @JsonGetter(JSON_OUTPUT_SETTINGS)
    public OutputSettings getSettings() {
        return this.settings;
    }

    @JsonSetter(JSON_OUTPUT_SETTINGS)
    public void setSettings(OutputSettings settings) {
        this.settings = settings;
    }
}
