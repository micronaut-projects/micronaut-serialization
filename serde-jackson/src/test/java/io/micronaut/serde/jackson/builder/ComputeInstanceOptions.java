package io.micronaut.serde.jackson.builder;


    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
        builder = ComputeInstanceOptions.Builder.class)
    @com.fasterxml.jackson.annotation.JsonTypeInfo(
        use = com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME,
        include = com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY,
        property = "instanceType")
    public class ComputeInstanceOptions extends InstanceConfigurationInstanceDetails {
        @com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder(withPrefix = "")
        public static class Builder {
            /** The Compute Instance Configuration parameters. */
            @com.fasterxml.jackson.annotation.JsonProperty("options")
            private java.util.List<ComputeInstanceDetails> options;

            @com.fasterxml.jackson.annotation.JsonProperty("options")
            public Builder options(java.util.List<ComputeInstanceDetails> options) {
                this.options = options;
                return this;
            }

            public ComputeInstanceOptions build() {
                ComputeInstanceOptions model = new ComputeInstanceOptions(this.options);
                return model;
            }
        }

    /** Create a new builder. */
    public static Builder builder() {
        return new Builder();
    }
    @com.fasterxml.jackson.annotation.JsonProperty("options")
    private final java.util.List<ComputeInstanceDetails> options;

    @Deprecated
    public ComputeInstanceOptions(java.util.List<ComputeInstanceDetails> options) {
        this.options = options;
    }

    public java.util.List<ComputeInstanceDetails> getOptions() {
        return options;
    }
}
