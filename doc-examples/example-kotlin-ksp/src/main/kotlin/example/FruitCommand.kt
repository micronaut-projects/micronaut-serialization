package example

import io.micronaut.serde.annotation.Serdeable
import jakarta.validation.constraints.NotEmpty

@Serdeable // <1>
data class FruitCommand(
    @field:NotEmpty val name: String, // <2>
    val description: String? = null // <3>
)
