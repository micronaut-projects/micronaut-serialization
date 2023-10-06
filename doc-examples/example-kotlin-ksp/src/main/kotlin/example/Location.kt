package example

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class Location(
    val features: Map<Feature, Point>
)
