package example

import io.micronaut.serde.annotation.Serdeable

@Serdeable
class Location {
    final Map<Feature, Point> features

    Location(Map<Feature, Point> features) {
        this.features = features
    }
}
