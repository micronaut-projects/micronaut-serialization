package example

import io.micronaut.serde.PropertyFilter
import io.micronaut.serde.Serializer
import jakarta.inject.Named
import jakarta.inject.Singleton

@Singleton
@Named("person-filter") // <1>
class PersonFilter : PropertyFilter {

    override fun shouldInclude(
            encoderContext: Serializer.EncoderContext,
            propertySerializer: Serializer<Any>,
            bean: Any,
            propertyName: String,
            propertyValue: Any?
    ): Boolean {
        if (bean is Person) { // <2>
            if (propertyName == "name") {
                return bean.preferredName == null
            } else if (propertyName == "preferredName") {
                return bean.preferredName != null
            }
        }
        return true
    }
}
