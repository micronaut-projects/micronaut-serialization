package example

import io.micronaut.serde.annotation.Serdeable

@Serdeable
class Place {
    @Serdeable.Serializable(using = ReversePointSerde.class) // <1>
    @Serdeable.Deserializable(using = ReversePointSerde.class) // <2>
    final Point point

    @Serdeable.Serializable(using = ReversePointSerde.class)
    final Point pointCustomSer

    @Serdeable.Deserializable(using = ReversePointSerde.class)
    final Point pointCustomDes

    Place(Point point, Point pointCustomSer, Point pointCustomDes) {
        this.point = point
        this.pointCustomSer = pointCustomSer
        this.pointCustomDes = pointCustomDes
    }
}
