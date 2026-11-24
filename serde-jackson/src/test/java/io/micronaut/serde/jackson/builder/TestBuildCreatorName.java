package io.micronaut.serde.jackson.builder;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.config.naming.IdentityStrategy;
import io.micronaut.serde.config.naming.SnakeCaseStrategy;
import tools.jackson.databind.annotation.JsonDeserialize;

@Serdeable.Serializable(naming = IdentityStrategy.class)
@Serdeable.Deserializable(naming = SnakeCaseStrategy.class)
@JsonDeserialize(builder = TestBuildCreatorName.Builder.class)
public class TestBuildCreatorName {
    private final String name;

    public TestBuildCreatorName(@JsonProperty("legacy") String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static class Builder {
        private String name = "unset";

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public TestBuildCreatorName build() {
            return new TestBuildCreatorName(name);
        }
    }
}
