package example

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class Item(val id: Int, val name: String, val count: Int)
