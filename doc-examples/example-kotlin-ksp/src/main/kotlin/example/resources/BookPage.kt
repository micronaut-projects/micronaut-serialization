package example.resources

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class BookPage(val books: List<EntityResource<Book>>, val authors: Map<String, EntityResource<Author>>)
