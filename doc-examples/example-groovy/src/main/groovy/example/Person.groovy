package example

import com.fasterxml.jackson.annotation.JsonFilter
import io.micronaut.serde.annotation.Serdeable

@Serdeable
@JsonFilter("person-filter") // <1>
class Person {
    String name
    String preferredName
}
