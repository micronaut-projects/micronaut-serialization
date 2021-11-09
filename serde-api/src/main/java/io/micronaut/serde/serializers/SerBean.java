/*
 * Copyright 2017-2021 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.serde.serializers;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanMethod;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.serde.SerdeIntrospections;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Internal
final class SerBean<T> {
    // CHECKSTYLE:OFF
    private final SerdeIntrospections introspections;
    @NonNull
    public final BeanIntrospection<T> introspection;
    public final List<SerProperty<T, Object>> writeProperties;
    @Nullable
    public final String wrapperProperty;
    @Nullable
    public final SerProperty<T, Object> anyGetter;
    // CHECKSTYLE:ON

    SerBean(Argument<T> definition, SerdeIntrospections introspections, Serializer.EncoderContext encoderContext) throws SerdeException {
        this.introspections = introspections;
        final AnnotationMetadata annotationMetadata = definition.getAnnotationMetadata();
        this.introspection = introspections.getSerializableIntrospection(definition);
        final Collection<BeanProperty<T, Object>> properties =
                introspection.getBeanProperties().stream()
                        .filter(property -> !property.isWriteOnly() &&
                                !property.booleanValue(SerdeConfig.class, SerdeConfig.IGNORED).orElse(false) &&
                                !property.booleanValue(SerdeConfig.class, SerdeConfig.READ_ONLY).orElse(false))
                        .collect(Collectors.toList());
        final Collection<BeanMethod<T, Object>> beanMethods = introspection.getBeanMethods();
        final List<BeanMethod<T, Object>> jsonGetters = new ArrayList<>(beanMethods.size());
        BeanMethod<T, Object> anyGetter = null;
        for (BeanMethod<T, Object> beanMethod : beanMethods) {
            if (beanMethod.isAnnotationPresent(SerdeConfig.Getter.class)) {
                jsonGetters.add(beanMethod);
            } else if (beanMethod.isAnnotationPresent(SerdeConfig.AnyGetter.class)) {
                anyGetter = beanMethod;
            }
        }
        SerProperty<T, Object> anyGetterProperty = anyGetter != null ? new SerProperty<>("any",
                anyGetter.getReturnType().asArgument(),
                anyGetter::invoke,
                encoderContext.findSerializer(anyGetter.getReturnType().asArgument()),
                null,
                encoderContext
        ) : null;

        if (!properties.isEmpty() || !jsonGetters.isEmpty()) {
            writeProperties = new ArrayList<>(properties.size() + jsonGetters.size());
            AnnotationMetadata am = new AnnotationMetadataHierarchy(introspection, definition.getAnnotationMetadata());
            am.stringValue(SerdeConfig.class, SerdeConfig.TYPE_NAME).ifPresent((typeName) -> {
                if (SerdeConfig.TYPE_NAME_CLASS_SIMPLE_NAME_PLACEHOLDER.equals(typeName)) {
                    typeName = introspection.getBeanType().getSimpleName();
                }
                String typeProperty = am.stringValue(SerdeConfig.class, SerdeConfig.TYPE_PROPERTY).orElse(null);
                if (typeProperty != null) {
                    try {
                        writeProperties.add(new SerProperty(typeProperty,
                                Argument.of(String.class, typeProperty),
                                null,
                                encoderContext.findSerializer(Argument.STRING),
                                typeName,
                                encoderContext
                        ));
                    } catch (SerdeException e) {
                        throw new IntrospectionException("Error configuring subtype binding for type " + introspection.getBeanType() + ": " + e.getMessage());
                    }
                }
            });
            for (BeanProperty<T, Object> property : properties) {
                final Argument<Object> argument = property.asArgument();
                final AnnotationMetadata propertyAnnotationMetadata = property.getAnnotationMetadata();
                final String defaultPropertyName = argument.getName();
                boolean unwrapped = propertyAnnotationMetadata.hasAnnotation(SerdeConfig.Unwrapped.class);
                if (unwrapped) {
                    BeanIntrospection<Object> propertyIntrospection = introspections.getSerializableIntrospection(property.asArgument());
                    for (BeanProperty<Object, Object> unwrappedProperty : propertyIntrospection.getBeanProperties()) {
                        Argument<Object> unwrappedPropertyArgument = unwrappedProperty.asArgument();
                        String n = resolveName(propertyAnnotationMetadata,
                                unwrappedProperty.getAnnotationMetadata(),
                                unwrappedPropertyArgument.getName(),
                                true);
                        final SerProperty<T, Object> serProperty = new SerProperty<>(n,
                                unwrappedPropertyArgument,
                                bean -> unwrappedProperty.get(property.get(bean)),
                                (Serializer<Object>) encoderContext.findSerializer(unwrappedPropertyArgument),
                                null,
                                encoderContext
                        );
                        writeProperties.add(serProperty);
                    }
                } else {
                    String n = resolveName(annotationMetadata, propertyAnnotationMetadata, defaultPropertyName, false);
                    final SerProperty<T, Object> serProperty;
                    try {
                        serProperty = new SerProperty<>(n,
                                                        argument,
                                                        property::get,
                                                        encoderContext.findSerializer(argument),
                                                        null,
                                                        encoderContext
                        );
                    } catch (SerdeException e) {
                        throw new SerdeException("Error resolving serializer for property [" + property + "] of type [" + argument.getType().getName() + "]: " + e.getMessage(), e);
                    }
                    if (propertyAnnotationMetadata.hasDeclaredAnnotation(SerdeConfig.AnyGetter.class)) {
                        anyGetterProperty = serProperty;
                    } else {
                        writeProperties.add(serProperty);
                    }
                }
            }

            for (BeanMethod<T, Object> jsonGetter : jsonGetters) {
                final AnnotationMetadata jsonGetterAnnotationMetadata = jsonGetter.getAnnotationMetadata();
                String n = resolveName(annotationMetadata, jsonGetterAnnotationMetadata, NameUtils.getPropertyNameForGetter(jsonGetter.getName()), false);
                final Argument<Object> returnType = jsonGetter.getReturnType().asArgument();
                writeProperties.add(new SerProperty<>(n,
                        returnType,
                        (Function<T, Object>) jsonGetter::invoke,
                        encoderContext.findSerializer(returnType),
                        null,
                        encoderContext
                    )
                );
            }
        } else {
            writeProperties = Collections.emptyList();
        }
        this.anyGetter = anyGetterProperty;
        wrapperProperty = introspection.stringValue(SerdeConfig.class, SerdeConfig.WRAPPER_PROPERTY).orElse(null);
    }

    private String resolveName(AnnotationMetadata annotationMetadata,
                             AnnotationMetadata propertyAnnotationMetadata,
                             String defaultPropertyName, boolean unwrapped) {
        String n =
                propertyAnnotationMetadata.stringValue(SerdeConfig.class, SerdeConfig.PROPERTY).orElse(defaultPropertyName);
        if (unwrapped) {
            n = annotationMetadata.stringValue(SerdeConfig.Unwrapped.class, SerdeConfig.Unwrapped.PREFIX)
                    .orElse("") + n + annotationMetadata.stringValue(SerdeConfig.Unwrapped.class, SerdeConfig.Unwrapped.SUFFIX)
                    .orElse("");
        }
        return n;
    }

    @Internal
    static final class SerProperty<B, P> {
        // CHECKSTYLE:OFF
        public final String name;
        public final Argument<P> argument;
        public final Serializer<P> serializer;
        public final SerdeConfig.Include include;
        private final @Nullable P injected;
        private final Function<B, P> reader;
        // CHECKSTYLE:ON

        public SerProperty(
                @NonNull String name,
                @NonNull Argument<P> argument,
                @Nullable Function<B, P> reader,
                @NonNull Serializer<P> serializer,
                @Nullable P injected,
                @NonNull Serializer.EncoderContext encoderContext) {
            this.name = name;
            this.argument = argument;
            this.reader = reader;
            this.serializer = serializer.createSpecific(argument, encoderContext);
            final AnnotationMetadata annotationMetadata = argument.getAnnotationMetadata();
            this.include = annotationMetadata
                    .enumValue(SerdeConfig.class, SerdeConfig.INCLUDE, SerdeConfig.Include.class)
                    .orElse(SerdeConfig.Include.ALWAYS);
            this.injected = injected;
        }

        @SuppressWarnings("unchecked")
        public P get(B bean) {
            if (injected != null) {
                return injected;
            } else {
                return reader.apply(bean);
            }
        }
    }
}
