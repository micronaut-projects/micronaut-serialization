package example;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(builder = @Introspected.IntrospectionBuilder(builderClass = ReleaseRequest.Builder.class))
public class ReleaseRequest {

    @JsonProperty(required = true) // <1>
    private final String service;

    @JsonProperty(defaultValue = "platform") // <2>
    private final String owner;

    private final String notes; // <3>

    private ReleaseRequest(String service, String owner, String notes) {
        this.service = service;
        this.owner = owner;
        this.notes = notes;
    }

    public String getService() {
        return service;
    }

    public String getOwner() {
        return owner;
    }

    public String getNotes() {
        return notes;
    }

    public static final class Builder {
        private String service;
        private String owner;
        private String notes;

        public Builder service(String service) {
            this.service = service;
            return this;
        }

        public Builder owner(String owner) {
            this.owner = owner;
            return this;
        }

        public Builder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public ReleaseRequest build() {
            return new ReleaseRequest(service, owner, notes);
        }
    }
}
