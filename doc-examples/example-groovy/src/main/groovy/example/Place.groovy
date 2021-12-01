package example

import io.micronaut.serde.annotation.Serdeable

@Serdeable
class Place {
    @Serdeable.Serializable(using = ReversePointSerde.class) // <1>
    @Serdeable.Deserializable(using = ReversePointSerde.class) // <2>
    final Point point

    Place(Point point) {
        this.point = point
    }
}
