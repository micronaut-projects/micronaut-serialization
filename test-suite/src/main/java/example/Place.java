package example;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class Place {
    @Serdeable.Serializable(using = ReversePointSerde.class) // <1>
    @Serdeable.Deserializable(using = ReversePointSerde.class) // <2>
    private final Point point;

    public Place(Point point) {
        this.point = point;
    }

    public Point getPoint() {
        return point;
    }
}
