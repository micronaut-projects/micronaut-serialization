package example

import io.micronaut.serde.PropertyFilter
import io.micronaut.serde.Serializer
import jakarta.inject.Named
import jakarta.inject.Singleton

@Singleton
@Named("person-filter") // <1>
class PersonFilter implements PropertyFilter {

    @Override
    boolean shouldInclude(
        Serializer.EncoderContext encoderContext, Serializer<Object> propertySerializer,
        Object bean, String propertyName, Object propertyValue
    ) {
        if (bean instanceof Person) { // <2>
            if (propertyName == "name") {
                return bean.preferredName == null
            } else if (propertyName == "preferredName") {
                return bean.preferredName != null
            }
        }
        return true
    }
}
