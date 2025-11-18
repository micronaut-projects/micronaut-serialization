package io.micronaut.serde.jackson.builder;

import java.util.List;

@jakarta.annotation.Generated(value = "OracleSDKGenerator", comments = "API Version: 20160918")
@com.fasterxml.jackson.databind.annotation.JsonDeserialize(
    builder = ComputeInstanceDetails.Builder.class)
@com.fasterxml.jackson.annotation.JsonTypeInfo(
    use = com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME,
    include = com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY,
    property = "instanceType")
public class ComputeInstanceDetails extends InstanceConfigurationInstanceDetails {
    private final List<String> launchDetails;

    @com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        @com.fasterxml.jackson.annotation.JsonProperty("launchDetails")
        private java.util.List<String> launchDetails;

        /**

         * @param launchDetails the value to set
         * @return this builder
         */
        public Builder launchDetails(
            java.util.List<String> launchDetails) {
            this.launchDetails = launchDetails;
            return this;
        }

        public ComputeInstanceDetails build() {
            ComputeInstanceDetails model = new ComputeInstanceDetails(this.launchDetails);
            return model;
        }
    }

    /** Create a new builder. */
    public static Builder builder() {
        return new Builder();
    }

    @Deprecated
    public ComputeInstanceDetails(
        java.util.List<String> launchDetails) {
        this.launchDetails = launchDetails;
    }

}
