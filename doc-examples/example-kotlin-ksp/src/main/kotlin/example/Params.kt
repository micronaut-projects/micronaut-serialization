package example

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class Params(
    val required: String,
    val stringDefault: String = "default",
    val boolDefault: Boolean = true,
    val longDefault: Long = 5,
)
