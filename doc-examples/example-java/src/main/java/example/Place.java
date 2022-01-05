package example;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class Place {
    @Serdeable.Serializable(using = ReversePointSerde.class) // <1>
    @Serdeable.Deserializable(using = ReversePointSerde.class) // <2>
    private final Point point;

    @Serdeable.Serializable(using = ReversePointSerde.class)
    private final Point pointCustomSer;

    @Serdeable.Deserializable(using = ReversePointSerde.class)
    private final Point pointCustomDes;

    public Place(Point point, Point pointCustomSer, Point pointCustomDes) {
        this.point = point;
        this.pointCustomSer = pointCustomSer;
        this.pointCustomDes = pointCustomDes;
    }

    public Point getPoint() {
        return point;
    }

    public Point getPointCustomSer() {
        return pointCustomSer;
    }

    public Point getPointCustomDes() {
        return pointCustomDes;
    }
}
