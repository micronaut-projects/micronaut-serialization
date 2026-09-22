package example.resources

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class Book(val id: Long?, val title: String, val author: String)
