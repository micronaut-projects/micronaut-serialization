package example

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class ProgrammaticCode(val value: String)
