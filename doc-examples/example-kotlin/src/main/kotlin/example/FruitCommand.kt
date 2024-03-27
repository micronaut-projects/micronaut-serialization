package example

import io.micronaut.serde.annotation.Serdeable
import jakarta.validation.constraints.NotEmpty

@Serdeable
data class FruitCommand(
    @field:NotEmpty val name: String,
    val description: String? = null
)
