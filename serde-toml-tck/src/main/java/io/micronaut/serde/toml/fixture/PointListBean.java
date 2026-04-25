package io.micronaut.serde.toml.fixture;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.Objects;

@Serdeable
@JsonPropertyOrder({"ids", "points"})
public class PointListBean {
    private List<String> ids;
    private List<Point> points;

    public PointListBean() {
    }

    public PointListBean(List<String> ids, List<Point> points) {
        this.ids = ids;
        this.points = points;
    }

    public List<String> getIds() {
        return ids;
    }

    public void setIds(List<String> ids) {
        this.ids = ids;
    }

    public List<Point> getPoints() {
        return points;
    }

    public void setPoints(List<Point> points) {
        this.points = points;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PointListBean that)) {
            return false;
        }
        return Objects.equals(ids, that.ids)
            && Objects.equals(points, that.points);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ids, points);
    }
}
