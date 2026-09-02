package io.micronaut.serde.jackson.builder;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder = TestBuildStrictMethod.Builder.class)
public class TestBuildStrictMethod {
    private final String service;
    private final String owner;

    private TestBuildStrictMethod(String service, String owner) {
        this.service = service;
        this.owner = owner;
    }

    public String getService() {
        return service;
    }

    public String getOwner() {
        return owner;
    }

    public static final class Builder {
        private String service;
        private String owner;

        public Builder service(@JsonProperty(required = true) String service) {
            this.service = service;
            return this;
        }

        public Builder owner(@JsonProperty(defaultValue = "platform") String owner) {
            this.owner = owner;
            return this;
        }

        public TestBuildStrictMethod build() {
            return new TestBuildStrictMethod(service, owner);
        }
    }
}
