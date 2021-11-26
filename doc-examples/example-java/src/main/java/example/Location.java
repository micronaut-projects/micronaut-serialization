package example;

import io.micronaut.serde.annotation.Serdeable;
import java.util.Map;

@Serdeable
public class Location {
    private final Map<Feature, Point> features;

    public Location(Map<Feature, Point> features) {
        this.features = features;
    }

    public Map<Feature, Point> getFeatures() {
        return features;
    }
}
