package example;

import io.micronaut.serde.PropertyFilter;
import io.micronaut.serde.Serializer;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@Named("person-filter") // <1>
public class PersonFilter implements PropertyFilter {

    @Override
    public boolean shouldInclude(
        Serializer.EncoderContext encoderContext, Serializer<Object> propertySerializer,
        Object bean, String propertyName, Object propertyValue
    ) {
        if (bean instanceof Person) { // <2>
            Person person = (Person) bean;
            if (propertyName.equals("name")) {
                return person.getPreferredName() == null;
            } else if (propertyName.equals("preferredName")) {
                return person.getPreferredName() != null;
            }
        }
        return true;
    }
}
