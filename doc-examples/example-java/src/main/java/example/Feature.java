package example;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import jakarta.inject.Singleton;

import java.util.Optional;

public class Feature {
    private final String name;

    public Feature(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    @Override
    public String toString() { // <1>
        return name;
    }

    @Singleton
    static class FeatureConverter implements TypeConverter<String, Feature> { // <2>
        @Override
        public Optional<Feature> convert(String object, Class<Feature> targetType, ConversionContext context) {
            return Optional.of(new Feature(object));
        }
    }
}
