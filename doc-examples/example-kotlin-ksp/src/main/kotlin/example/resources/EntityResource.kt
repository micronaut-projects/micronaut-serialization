package example.resources

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonUnwrapped
import io.micronaut.serde.annotation.Serdeable

@Serdeable
class EntityResource<T> @JsonCreator constructor(@param:JsonUnwrapped @get:JsonUnwrapped val content: T)
