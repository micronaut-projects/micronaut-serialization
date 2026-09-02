package io.micronaut.serde.jackson.compiletime;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.SerdeableGenerated;
import tools.jackson.databind.annotation.JsonDeserialize;

@SerdeableGenerated(required = false)
@JsonDeserialize(builder = SourceGenBuilderShape.Builder.class)
public final class SourceGenBuilderShape {
    @JsonProperty(required = true)
    private final String service;
    @JsonProperty(defaultValue = "platform")
    private final String owner;

    private SourceGenBuilderShape(String service, String owner) {
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

        public Builder service(String service) {
            this.service = service;
            return this;
        }

        public Builder owner(String owner) {
            this.owner = owner;
            return this;
        }

        public SourceGenBuilderShape build() {
            return new SourceGenBuilderShape(service, owner);
        }
    }
}
