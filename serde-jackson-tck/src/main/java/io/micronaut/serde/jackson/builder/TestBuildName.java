package io.micronaut.serde.jackson.builder;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder = TestBuildName.Builder.class)
public class TestBuildName {
    @JsonProperty("bar")
    private final String foo;

    private TestBuildName(String foo) {
        this.foo = foo;
    }

    public String getFoo() {
        return foo;
    }

    public static class Builder {
        private String foo;

        public Builder foo(String foo) {
            this.foo = foo;
            return this;
        }

        public TestBuildName build() {
            return new TestBuildName(foo);
        }
    }
}
