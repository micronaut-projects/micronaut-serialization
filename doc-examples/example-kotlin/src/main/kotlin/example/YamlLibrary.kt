package example

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class YamlLibrary(val name: String, val books: List<String>)
