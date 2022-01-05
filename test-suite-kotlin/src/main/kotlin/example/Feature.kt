package example

import io.micronaut.core.convert.ConversionContext
import io.micronaut.core.convert.TypeConverter
import jakarta.inject.Singleton
import java.util.*

class Feature(private val name: String) {
    fun name(): String {
        return name
    }

    override fun toString(): String { // <1>
        return name
    }
}

@Singleton
class FeatureConverter : TypeConverter<String, Feature> {
    // <2>
    override fun convert(value: String, targetType: Class<Feature>, context: ConversionContext): Optional<Feature> {
        return Optional.of(Feature(value))
    }
}