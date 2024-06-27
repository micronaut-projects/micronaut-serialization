
package example

import io.micronaut.serde.annotation.Serdeable

@Serdeable
class NullPropertyDto {
    var longField: Long? = null
}
