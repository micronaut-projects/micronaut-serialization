package io.micronaut.serde.beans;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;

@Internal
public final class SerIntrospection<T> {
    @NonNull
    public final BeanIntrospection<T> introspection;
    public final Map<String, SerProperty<T, Object>> writeProperties;

    public SerIntrospection(@NonNull BeanIntrospection<T> introspection, Serializer.EncoderContext encoderContext)
            throws SerdeException {
        this.introspection = introspection;
        final Collection<BeanProperty<T, Object>> properties =
                introspection.getBeanProperties().stream()
                        .filter(property -> !property.isWriteOnly() &&
                                !property.booleanValue(SerdeConfig.class, "ignored").orElse(false) &&
                                !property.booleanValue(SerdeConfig.class, "readOnly").orElse(false))
                        .collect(Collectors.toList());
        if (!properties.isEmpty()) {
            writeProperties = new LinkedHashMap<>(properties.size());
            for (BeanProperty<T, Object> property : properties) {
                final Argument<Object> argument = property.asArgument();
                final String n = property.stringValue(SerdeConfig.class, "property").orElse(argument.getName());

                writeProperties.put(n, new SerProperty<>(argument, property, encoderContext.findSerializer(argument)));
            }
        } else {
            writeProperties = Collections.emptyMap();
        }
    }

    @Internal
    public static final class SerProperty<B, P> {
        public final Argument<P> argument;
        public final BeanProperty<B, Object> reader;
        public final Serializer<P> serializer;
        public final SerdeConfig.Include include;
        public final boolean unwrapped;

        public SerProperty(Argument<P> argument, BeanProperty<B, Object> reader, Serializer<P> serializer) {
            this.argument = argument;
            this.reader = reader;
            this.serializer = serializer;
            this.include = reader.enumValue(SerdeConfig.class, SerdeConfig.INCLUDE, SerdeConfig.Include.class)
                    .orElse(SerdeConfig.Include.ALWAYS);
            this.unwrapped = reader.hasAnnotation(SerdeConfig.Unwrapped.NAME);
        }

        @SuppressWarnings("unchecked")
        public P get(B bean) {
            return (P) reader.get(bean);
        }
    }
}
