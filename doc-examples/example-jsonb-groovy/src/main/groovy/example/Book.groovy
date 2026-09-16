package example

import io.micronaut.serde.annotation.Serdeable
import jakarta.json.bind.annotation.JsonbCreator
import jakarta.json.bind.annotation.JsonbProperty

@Serdeable // <1>
class Book {
    final String title
    @JsonbProperty("qty") // <2>
    final int quantity

    @JsonbCreator // <3>
    Book(String title, int quantity) {
        this.title = title
        this.quantity = quantity
    }
}
