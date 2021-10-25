package io.micronaut.serde;

import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;

@Singleton
public class DefaultSerdeIntrospections implements SerdeIntrospections{
    @Override
    public <T> BeanIntrospection<T> getSerializableIntrospection(Argument<T> type) {
        final BeanIntrospection<T> introspection = BeanIntrospector.SHARED.getIntrospection(type.getType());
        if (introspection.hasStereotype(Serdeable.Serializable.class)) {
            return introspection;
        } else {
            throw new IntrospectionException("No serializable introspection present for type. Consider adding Serdeable.Serializable annotate to type " + type);
        }

    }

    @Override
    public <T> BeanIntrospection<T> getDeserializableIntrospection(Argument<T> type) {
        final BeanIntrospection<T> introspection = BeanIntrospector.SHARED.getIntrospection(type.getType());
        if (introspection.hasStereotype(Serdeable.Deserializable.class)) {
            return introspection;
        } else {
            throw new IntrospectionException("No deserializable introspection present for type. Consider adding Serdeable.Deserializable annotate to type " + type);
        }
    }
}
