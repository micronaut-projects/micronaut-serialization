package io.micronaut.serde.jackson.builder;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder = TestBuildStrict.Builder.class)
public class TestBuildStrict {
    @JsonProperty(required = true)
    private final String service;
    @JsonProperty(defaultValue = "platform")
    private final String owner;
    @JsonProperty(defaultValue = "3")
    private final int retries;
    private final String notes;

    private TestBuildStrict(String service, String owner, int retries, String notes) {
        this.service = service;
        this.owner = owner;
        this.retries = retries;
        this.notes = notes;
    }

    public String getService() {
        return service;
    }

    public String getOwner() {
        return owner;
    }

    public int getRetries() {
        return retries;
    }

    public String getNotes() {
        return notes;
    }

    public static final class Builder {
        private String service;
        private String owner;
        private int retries;
        private String notes = "none";

        public Builder service(String service) {
            this.service = service;
            return this;
        }

        public Builder owner(String owner) {
            this.owner = owner;
            return this;
        }

        public Builder retries(int retries) {
            this.retries = retries;
            return this;
        }

        public Builder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public TestBuildStrict build() {
            return new TestBuildStrict(service, owner, retries, notes);
        }
    }
}
