package example

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable

@Serdeable
@Introspected(builder = @Introspected.IntrospectionBuilder(builderClass = ReleaseRequest.Builder))
class ReleaseRequest {

    @JsonProperty(required = true) // <1>
    final String service

    @JsonProperty(defaultValue = "platform") // <2>
    final String owner

    final String notes // <3>

    private ReleaseRequest(String service, String owner, String notes) {
        this.service = service
        this.owner = owner
        this.notes = notes
    }

    static final class Builder {
        private String service
        private String owner
        private String notes

        Builder service(String service) {
            this.service = service
            return this
        }

        Builder owner(String owner) {
            this.owner = owner
            return this
        }

        Builder notes(String notes) {
            this.notes = notes
            return this
        }

        ReleaseRequest build() {
            return new ReleaseRequest(service, owner, notes)
        }
    }
}
