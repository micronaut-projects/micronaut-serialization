package example

import io.micronaut.serde.annotation.Serdeable
import jakarta.json.bind.annotation.JsonbCreator
import jakarta.json.bind.annotation.JsonbProperty

@Serdeable // <1>
class Book @JsonbCreator constructor( // <3>
    val title: String,
    @field:JsonbProperty("qty") val quantity: Int // <2>
)
