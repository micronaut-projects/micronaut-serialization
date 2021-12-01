package example

import io.micronaut.core.convert.ConversionContext
import io.micronaut.core.convert.TypeConverter
import jakarta.inject.Singleton

class Feature {
    private final String name

    Feature(String name) {
        this.name = name
    }

    String name() {
        return name
    }

    @Override
    String toString() { // <1>
        return name
    }

    @Singleton
    static class FeatureConverter implements TypeConverter<String, Feature> { // <2>
        @Override
        Optional<Feature> convert(String object, Class<Feature> targetType, ConversionContext context) {
            return Optional.of(new Feature(object))
        }
    }
}
