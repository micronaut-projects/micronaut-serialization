package example

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class NonNullDto(
    val longField: Long,
)
