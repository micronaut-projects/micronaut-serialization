package example

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable

@Serdeable // <1>
data class Book (
    val title: String, // <2>
    @JsonProperty("qty") val quantity: Int
)