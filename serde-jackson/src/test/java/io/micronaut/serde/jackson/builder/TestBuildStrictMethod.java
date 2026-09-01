package io.micronaut.serde.jackson.builder;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder = TestBuildStrictMethod.Builder.class)
public class TestBuildStrictMethod {
    private final String service;

    private TestBuildStrictMethod(String service) {
        this.service = service;
    }

    public String getService() {
        return service;
    }

    public static final class Builder {
        private String service;

        public Builder service(@JsonProperty(required = true) String service) {
            this.service = service;
            return this;
        }

        public TestBuildStrictMethod build() {
            return new TestBuildStrictMethod(service);
        }
    }
}
