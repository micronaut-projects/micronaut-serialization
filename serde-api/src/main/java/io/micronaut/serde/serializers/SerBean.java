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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
import io.micronaut.serde.Serializer;
import io.micronaut.serde.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;

@Internal
final class SerBean<T> {
    // CHECKSTYLE:OFF
    @NonNull
    public final BeanIntrospection<T> introspection;
    public final Map<String, SerProperty<T, Object>> writeProperties;
    public final boolean unwrapped;
    @Nullable
    public final String wrapperProperty;
    @Nullable
    public final SerProperty<T, Object> anyGetter;
    // CHECKSTYLE:ON

    SerBean(
            Argument<T> definition,
            @NonNull BeanIntrospection<T> introspection,
            Serializer.EncoderContext encoderContext)
            throws SerdeException {
        final AnnotationMetadata annotationMetadata = definition.getAnnotationMetadata();
        this.unwrapped = annotationMetadata.hasAnnotation(SerdeConfig.Unwrapped.class);
        this.introspection = introspection;
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
        SerProperty<T, Object> anyGetterProperty = anyGetter != null ? new SerProperty<>(
                anyGetter.getReturnType().asArgument(),
                anyGetter::invoke,
                encoderContext.findSerializer(anyGetter.getReturnType().asArgument()),
                null
        ) : null;

        if (!properties.isEmpty() || !jsonGetters.isEmpty()) {
            writeProperties = new LinkedHashMap<>(properties.size() + jsonGetters.size());
            introspection.stringValue(SerdeConfig.class, SerdeConfig.TYPE_NAME).ifPresent((typeName) -> {
                introspection.stringValue(SerdeConfig.class, SerdeConfig.TYPE_PROPERTY).ifPresent((typeProperty) -> {
                    try {
                        writeProperties.put(typeProperty, new SerProperty(
                                Argument.of(String.class, typeProperty),
                                null,
                                encoderContext.findSerializer(Argument.STRING),
                                typeName
                        ));
                    } catch (SerdeException e) {
                        throw new IntrospectionException("Error configuring subtype binding for type " + introspection.getBeanType() + ": " + e.getMessage());
                    }
                });
            });
            for (BeanProperty<T, Object> property : properties) {
                final Argument<Object> argument = property.asArgument();
                final AnnotationMetadata propertyAnnotationMetadata = property.getAnnotationMetadata();
                final String defaultPropertyName = argument.getName();
                String n = resolveName(annotationMetadata, propertyAnnotationMetadata, defaultPropertyName);
                final SerProperty<T, Object> serProperty = new SerProperty<>(
                        argument,
                        property::get,
                        encoderContext.findSerializer(argument),
                        null);
                if (propertyAnnotationMetadata.hasDeclaredAnnotation(SerdeConfig.AnyGetter.class)) {
                    anyGetterProperty = serProperty;
                } else {
                    writeProperties.put(n, serProperty);
                }
            }

            for (BeanMethod<T, Object> jsonGetter : jsonGetters) {
                final AnnotationMetadata jsonGetterAnnotationMetadata = jsonGetter.getAnnotationMetadata();
                String n = resolveName(annotationMetadata, jsonGetterAnnotationMetadata, NameUtils.getPropertyNameForGetter(jsonGetter.getName()));
                final Argument<Object> returnType = jsonGetter.getReturnType().asArgument();
                writeProperties.put(n, new SerProperty<>(
                        returnType,
                        (Function<T, Object>) jsonGetter::invoke,
                        encoderContext.findSerializer(returnType),
                        null)
                );
            }
        } else {
            writeProperties = Collections.emptyMap();
        }
        this.anyGetter = anyGetterProperty;
        wrapperProperty = introspection.stringValue(SerdeConfig.class, SerdeConfig.WRAPPER_PROPERTY).orElse(null);
    }

    private String resolveName(AnnotationMetadata annotationMetadata,
                             AnnotationMetadata propertyAnnotationMetadata,
                             String defaultPropertyName) {
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
        public final Argument<P> argument;
        public final Serializer<P> serializer;
        public final SerdeConfig.Include include;
        public final boolean unwrapped;
        private final @Nullable P injected;
        private final Function<B, P> reader;
        // CHECKSTYLE:ON

        public SerProperty(
                Argument<P> argument,
                @Nullable Function<B, P> reader,
                Serializer<P> serializer,
                @Nullable P injected) {
            this.argument = argument;
            this.reader = reader;
            this.serializer = serializer;
            final AnnotationMetadata annotationMetadata = argument.getAnnotationMetadata();
            this.include = annotationMetadata
                    .enumValue(SerdeConfig.class, SerdeConfig.INCLUDE, SerdeConfig.Include.class)
                    .orElse(SerdeConfig.Include.ALWAYS);
            this.unwrapped = annotationMetadata.hasAnnotation(SerdeConfig.Unwrapped.NAME);
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
