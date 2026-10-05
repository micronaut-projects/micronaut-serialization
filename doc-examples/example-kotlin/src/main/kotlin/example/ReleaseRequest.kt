package example

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable

@Serdeable
@Introspected(builder = Introspected.IntrospectionBuilder(builderClass = ReleaseRequest.Builder::class))
class ReleaseRequest private constructor(
    @field:JsonProperty(required = true) // <1>
    val service: String,

    @field:JsonProperty(defaultValue = "platform") // <2>
    val owner: String,

    val notes: String? // <3>
) {

    class Builder {
        private var service: String? = null
        private var owner: String? = null
        private var notes: String? = null

        fun service(service: String?): Builder {
            this.service = service
            return this
        }

        fun owner(owner: String?): Builder {
            this.owner = owner
            return this
        }

        fun notes(notes: String?): Builder {
            this.notes = notes
            return this
        }

        fun build(): ReleaseRequest = ReleaseRequest(service!!, owner!!, notes)
    }
}
