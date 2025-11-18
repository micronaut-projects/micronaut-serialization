package io.micronaut.serde.jackson.builder;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.micronaut.core.annotation.Introspected;

@JsonDeserialize(
    builder = InstanceConfiguration.Builder.class
)
@JsonFilter("explicitlySetFilter")
public class InstanceConfiguration {

    @JsonProperty("instanceDetails")
    private final InstanceConfigurationInstanceDetails instanceDetails;

    public InstanceConfiguration(InstanceConfigurationInstanceDetails instanceDetails) {
        this.instanceDetails = instanceDetails;
    }

    public static Builder builder() {
        return new Builder();
    }

    public InstanceConfigurationInstanceDetails getInstanceDetails() {
        return instanceDetails;
    }

    @JsonPOJOBuilder(
        withPrefix = ""
    )
    public static class Builder {
        @JsonProperty("instanceDetails")
        private InstanceConfigurationInstanceDetails instanceDetails;

        public Builder instanceDetails(InstanceConfigurationInstanceDetails instanceDetails) {
            this.instanceDetails = instanceDetails;
            return this;
        }

        public InstanceConfiguration build() {
            InstanceConfiguration model = new InstanceConfiguration(this.instanceDetails);
            return model;
        }
    }
}
