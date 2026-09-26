package example

import io.micronaut.serde.annotation.Serdeable

@Serdeable // <1>
class Book {
    final String title
    final int quantity

    Book(String title, int quantity) {
        this.title = title
        this.quantity = quantity
    }
}
