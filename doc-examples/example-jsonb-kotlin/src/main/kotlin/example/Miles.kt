package example

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class Miles(val value: Int)
