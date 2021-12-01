package example

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable

@Serdeable // <1>
class Book {
    final String title
    @JsonProperty("qty") // <2>
    final int quantity

    @JsonCreator
    Book(String title, int quantity) { // <3>
        this.title = title
        this.quantity = quantity
    }
}
