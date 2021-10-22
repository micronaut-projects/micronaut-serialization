package io.micronaut.serde.deserializers;

import java.io.IOException;

import io.micronaut.context.annotation.Primary;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;
import jakarta.inject.Singleton;

@Singleton
@Primary
public class ObjectDeserializer implements Deserializer<Object> {
    @Override
    public Object deserialize(Decoder decoder,
                              DecoderContext decoderContext,
                              Argument<? super Object> type,
                              Argument<?>... generics) throws IOException {
        final BeanIntrospection<? super Object> introspection;
        try {
            introspection = decoderContext.getDeserializableIntrospection(type);
        } catch (IntrospectionException e) {
            throw new SerdeException("Unable to deserialize object of type: " + type, e);
        }
        final Argument<?>[] constructorArguments = introspection.getConstructorArguments();
        if (ArrayUtils.isEmpty(constructorArguments)) {
            // no constructors so no need to buffer
            final Object obj = introspection.instantiate();
            final Decoder objectDecoder = decoder.decodeObject();
            while (true) {
                final String prop = objectDecoder.decodeKey();
                if (prop == null) {
                    break;
                }
                BeanProperty<? super Object, Object> beanProperty =
                        introspection.getIndexedProperty(SerdeConfig.class, "property").orElse(null);
                if (beanProperty == null || beanProperty.isReadOnly()) {
                    beanProperty = introspection.getProperty(prop).orElse(null);
                }

                if (beanProperty != null && !beanProperty.isReadOnly()) {
                    final Argument<Object> propertyType = beanProperty.asArgument();
                    final Deserializer<?> deserializer = decoderContext.findDeserializer(propertyType);
                    final Object val = deserializer.deserialize(
                            objectDecoder,
                            decoderContext,
                            propertyType,
                            propertyType.getTypeParameters()
                    );
                    beanProperty.set(obj, val);
                } else {
                    objectDecoder.skipValue();
                }
            }

            objectDecoder.finishStructure();
            return obj;
        }
        throw new SerdeException("Unable to deserialize object of type: " + type);
    }
}
