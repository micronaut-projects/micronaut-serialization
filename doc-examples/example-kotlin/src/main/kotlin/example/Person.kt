package example

import com.fasterxml.jackson.annotation.JsonFilter
import io.micronaut.serde.annotation.Serdeable

@Serdeable
@JsonFilter("person-filter") // <1>
data class Person(
    val name: String,
    val preferredName: String?
)
