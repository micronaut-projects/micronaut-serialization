package example.resources

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class CollectionResource<T>(val items: List<EntityResource<T>>, val total: Int)
