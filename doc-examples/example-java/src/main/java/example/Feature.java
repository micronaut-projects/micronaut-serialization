package example;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class Feature {
    private final String name;
    private final Point point;

    public Feature(String name, Point point) {
        this.name = name;
        this.point = point;
    }

    public String getName() {
        return name;
    }

    public Point getPoint() {
        return point;
    }
}
