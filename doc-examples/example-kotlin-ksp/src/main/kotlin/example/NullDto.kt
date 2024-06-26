
package example

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class NullDto(
    val longField: Long? = null
)
