package example.resources

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class Author(val username: String, val name: String)
