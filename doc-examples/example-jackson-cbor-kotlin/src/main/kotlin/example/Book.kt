package example

import io.micronaut.serde.annotation.Serdeable

@Serdeable // <1>
class Book(
    val title: String,
    val quantity: Int
)
