package io.micronaut.serde.beans;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;

@Internal
public final class DeserIntrospection<T> {
    @NonNull
    public final BeanIntrospection<T> introspection;
    @Nullable
    public final Map<String, SerdeProperty<T, ?>> creatorParams;
    @Nullable
    public final Map<String, SerdeProperty<T, ?>> readProperties;

    public DeserIntrospection(BeanIntrospection<T> introspection, Deserializer.DecoderContext decoderContext)
            throws SerdeException {
        this.introspection = introspection;
        final Argument<?>[] constructorArguments = introspection.getConstructorArguments();
        final HashMap<String, SerdeProperty<T, ?>> creatorParams = new HashMap<>(constructorArguments.length);
        for (int i = 0; i < constructorArguments.length; i++) {
            Argument<?> constructorArgument = constructorArguments[i];
            final String jsonProperty = constructorArgument.getAnnotationMetadata()
                    .stringValue(SerdeConfig.class, SerdeConfig.PROPERTY)
                    .orElse(constructorArgument.getName());
            final Deserializer<?> deserializer = decoderContext.findDeserializer(constructorArgument);
            creatorParams.put(
                    jsonProperty,
                    new SerdeProperty<>(
                            i,
                            constructorArgument,
                            null,
                            (Deserializer) deserializer
                    )
            );
        }
        if (creatorParams.isEmpty()) {
            this.creatorParams = null;
        } else {
            this.creatorParams = Collections.unmodifiableMap(creatorParams);
        }

        final List<BeanProperty<T, Object>> beanProperties = introspection.getBeanProperties()
                .stream().filter(bp -> {
                    final AnnotationMetadata annotationMetadata = bp.getAnnotationMetadata();
                    return !bp.isReadOnly() &&
                            !annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.READ_ONLY).orElse(false) &&
                            !annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.IGNORED).orElse(false);
                }).collect(Collectors.toList());

        if (CollectionUtils.isNotEmpty(beanProperties)) {
            final HashMap<String, SerdeProperty<T, ?>> readProps = new HashMap<>(beanProperties.size());
            for (int i = 0; i < beanProperties.size(); i++) {
                BeanProperty<T, Object> beanProperty = beanProperties.get(i);
                final String jsonProperty = beanProperty.getAnnotationMetadata()
                        .stringValue(SerdeConfig.class, SerdeConfig.PROPERTY)
                        .orElse(beanProperty.getName());
                final Argument<Object> t = beanProperty.asArgument();
                final Deserializer<?> deserializer = decoderContext.findDeserializer(t);
                readProps.put(jsonProperty, new SerdeProperty<>(
                        i,
                        t,
                        beanProperty,
                        (Deserializer) deserializer)
                );

            }
            this.readProperties = Collections.unmodifiableMap(readProps);
        } else {
            readProperties = null;
        }
    }

    @Internal
    public static class SerdeProperty<B, P> {
        public final int index;
        public final Argument<P> argument;
        @Nullable
        public final P defaultValue;
        public final boolean required;
        public final @Nullable BeanProperty<B, P> writer;
        public final Deserializer<? super P> deserializer;

        public SerdeProperty(int index,
                             Argument<P> argument,
                             @Nullable BeanProperty<B, P> writer,
                             Deserializer<P> deserializer) {
            this.index = index;
            this.argument = argument;
            this.required = argument.isNonNull();
            this.writer = writer;
            this.deserializer = deserializer;
            // compute default
            this.defaultValue = argument.getAnnotationMetadata()
                    .getValue(Bindable.class, "defaultValue", argument)
                    .orElse(deserializer.getDefaultValue());
        }


    }
}
