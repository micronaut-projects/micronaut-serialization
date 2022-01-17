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
package io.micronaut.serde.support.serializers;

import io.micronaut.core.annotation.AnnotatedElement;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanMethod;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.serde.SerdeIntrospections;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.SerializationConfiguration;
import io.micronaut.serde.support.util.SerdeAnnotationUtil;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.util.TypeKey;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Internal
final class SerBean<T> {
    private static final Comparator<BeanProperty<?, Object>> BEAN_PROPERTY_COMPARATOR = (o1, o2) -> OrderUtil.COMPARATOR.compare(
            new Ordered() {
                @Override
                public int getOrder() {
                    return o1.intValue(Order.class).orElse(0);
                }
            }, new Ordered() {
                @Override
                public int getOrder() {
                    return o2.intValue(Order.class).orElse(0);
                }
            }
    );
    private static final String JK_PROP = "com.fasterxml.jackson.annotation.JsonProperty";
    private static final String JACKSON_VALUE = "com.fasterxml.jackson.annotation.JsonValue";

    // CHECKSTYLE:OFF
    @NonNull
    public final BeanIntrospection<T> introspection;
    public final List<SerProperty<T, Object>> writeProperties;
    @Nullable
    public final String wrapperProperty;
    @Nullable
    public final SerProperty<T, Object> anyGetter;
    public final SerProperty<T, Object> jsonValue;
    public final SerializationConfiguration configuration;
    public final boolean initialized;
    // CHECKSTYLE:ON

    SerBean(TypeKey typeKey,
            Map<TypeKey, SerBean<Object>> serBeanMap,
            Argument<T> definition,
            SerdeIntrospections introspections,
            Serializer.EncoderContext encoderContext,
            SerializationConfiguration configuration) throws SerdeException {
        //noinspection unchecked
        this.configuration = configuration;
        final AnnotationMetadata annotationMetadata = definition.getAnnotationMetadata();
        this.introspection = introspections.getSerializableIntrospection(definition);
        PropertyNamingStrategy entityPropertyNamingStrategy = getPropertyNamingStrategy(introspection, encoderContext, null);
        serBeanMap.put(typeKey, (SerBean<Object>) this);
        final Collection<Map.Entry<BeanProperty<T, Object>, AnnotationMetadata>> properties =
                introspection.getBeanProperties().stream()
                        .filter(property -> !property.isWriteOnly() &&
                                !property.booleanValue(SerdeConfig.class, SerdeConfig.IGNORED).orElse(false) &&
                                !property.booleanValue(SerdeConfig.class, SerdeConfig.READ_ONLY).orElse(false))
                        .sorted(getPropertyComparator())
                        .map(beanProperty -> {
                            Optional<Argument<?>> constructorArgument = Arrays.stream(introspection.getConstructor().getArguments())
                                    .filter(a -> a.getName().equals(beanProperty.getName()) && a.getType().equals(beanProperty.getType()))
                                    .findFirst();
                            return constructorArgument.<Map.Entry<BeanProperty<T, Object>, AnnotationMetadata>>map(argument -> new AbstractMap.SimpleEntry<>(
                                    beanProperty,
                                    new AnnotationMetadataHierarchy(argument.getAnnotationMetadata(), beanProperty.getAnnotationMetadata())
                            )).orElseGet(() -> new AbstractMap.SimpleEntry<>(
                                    beanProperty,
                                    beanProperty.getAnnotationMetadata()
                            ));
                        })
                        .collect(Collectors.toList());
        final Map.Entry<BeanProperty<T, Object>, AnnotationMetadata> serPropEntry = properties.stream()
                .filter(bp -> bp.getValue().hasAnnotation(SerdeConfig.SerValue.class) || bp.getValue().hasAnnotation(JACKSON_VALUE))
                .findFirst().orElse(null);
        if (serPropEntry != null) {
            wrapperProperty = null;
            anyGetter = null;
            BeanProperty<T, Object> beanProperty = serPropEntry.getKey();
            final Argument<Object> serType = beanProperty.asArgument();
            AnnotationMetadata propertyAnnotationMetadata = serPropEntry.getValue();
            jsonValue = new SerProperty<>(
                    this,
                    beanProperty.getName(),
                    serType,
                    propertyAnnotationMetadata,
                    beanProperty::get,
                    findSerializer(encoderContext, serType, propertyAnnotationMetadata),
                    null,
                    encoderContext
            );
            writeProperties = Collections.emptyList();
        } else {
            final Collection<BeanMethod<T, Object>> beanMethods = introspection.getBeanMethods();
            final BeanMethod<T, Object> serMethod = beanMethods.stream()
                    .filter(m -> m.isAnnotationPresent(SerdeConfig.SerValue.class) || m.getAnnotationMetadata().hasAnnotation(JACKSON_VALUE))
                    .findFirst().orElse(null);
            if (serMethod != null) {
                wrapperProperty = null;
                anyGetter = null;
                final Argument<Object> serType = serMethod.getReturnType().asArgument();
                jsonValue = new SerProperty<>(
                        this,
                        serMethod.getName(),
                        serType,
                        serMethod::invoke,
                        findSerializer(encoderContext, serType, serMethod.getAnnotationMetadata()),
                        null,
                        encoderContext
                );
                writeProperties = Collections.emptyList();
            } else {

                jsonValue = null;

                final List<BeanMethod<T, Object>> jsonGetters = new ArrayList<>(beanMethods.size());
                BeanMethod<T, Object> anyGetter = null;
                for (BeanMethod<T, Object> beanMethod : beanMethods) {
                    if (beanMethod.isAnnotationPresent(SerdeConfig.Getter.class)) {
                        jsonGetters.add(beanMethod);
                    } else if (beanMethod.isAnnotationPresent(SerdeConfig.AnyGetter.class)) {
                        anyGetter = beanMethod;
                    }
                }
                SerProperty<T, Object> anyGetterProperty = anyGetter != null ? new SerProperty<>(this, "any",
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
                        String injectedValue;
                        Function<T, String> reader;
                        if (SerdeConfig.TYPE_NAME_CLASS_SIMPLE_NAME_PLACEHOLDER.equals(typeName)) {
                            injectedValue = null;
                            reader = t -> t.getClass().getSimpleName();
                        } else {
                            injectedValue = typeName;
                            reader = null;
                        }
                        String typeProperty = am.stringValue(SerdeConfig.class, SerdeConfig.TYPE_PROPERTY).orElse(null);
                        if (typeProperty != null) {
                            try {
                                writeProperties.add(new SerProperty(this, typeProperty,
                                                                    Argument.of(String.class, typeProperty),
                                                                    reader,
                                                                    encoderContext.findSerializer(Argument.STRING),
                                                                    injectedValue,
                                                                    encoderContext
                                ));
                            } catch (SerdeException e) {
                                throw new IntrospectionException("Error configuring subtype binding for type " + introspection.getBeanType() + ": " + e.getMessage());
                            }
                        }
                    });
                    for (Map.Entry<BeanProperty<T, Object>, AnnotationMetadata> propWithAnnotations : properties) {
                        final BeanProperty<T, Object> property = propWithAnnotations.getKey();
                        final Argument<Object> argument = property.asArgument();
                        final AnnotationMetadata propertyAnnotationMetadata = propWithAnnotations.getValue();
                        final String defaultPropertyName = argument.getName();
                        boolean unwrapped = propertyAnnotationMetadata.hasAnnotation(SerdeConfig.Unwrapped.class);
                        PropertyNamingStrategy propertyNamingStrategy = getPropertyNamingStrategy(property.getAnnotationMetadata(), encoderContext, entityPropertyNamingStrategy);
                        if (unwrapped) {
                            BeanIntrospection<Object> propertyIntrospection = introspections.getSerializableIntrospection(property.asArgument());
                            for (BeanProperty<Object, Object> unwrappedProperty : propertyIntrospection.getBeanProperties()) {
                                Argument<Object> unwrappedPropertyArgument = unwrappedProperty.asArgument();
                                String n = resolveName(propertyAnnotationMetadata,
                                                       unwrappedProperty.getAnnotationMetadata(),
                                                       unwrappedPropertyArgument.getName(),
                                                       true, propertyNamingStrategy);
                                final AnnotationMetadataHierarchy combinedMetadata =
                                        new AnnotationMetadataHierarchy(
                                                argument.getAnnotationMetadata(),
                                                unwrappedProperty.getAnnotationMetadata()
                                        );
                                final SerProperty<T, Object> serProperty = new SerProperty<>(this, n,
                                                                                             unwrappedPropertyArgument,
                                                                                             combinedMetadata,
                                                                                             bean -> unwrappedProperty.get(property.get(bean)),
                                                                                             findSerializer(encoderContext, unwrappedPropertyArgument,
                                                                                                            argument.getAnnotationMetadata()),
                                                                                             null,
                                                                                             encoderContext
                                );
                                writeProperties.add(serProperty);
                            }
                        } else {
                            String n = resolveName(annotationMetadata, propertyAnnotationMetadata, defaultPropertyName, false, propertyNamingStrategy);
                            final SerProperty<T, Object> serProperty;
                            try {
                                serProperty = new SerProperty<>(this, n,
                                                                argument,
                                                                property::get,
                                                                findSerializer(encoderContext, argument, propertyAnnotationMetadata),
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
                        PropertyNamingStrategy propertyNamingStrategy = getPropertyNamingStrategy(jsonGetter.getAnnotationMetadata(), encoderContext, entityPropertyNamingStrategy);
                        final AnnotationMetadata jsonGetterAnnotationMetadata = jsonGetter.getAnnotationMetadata();
                        final String propertyName = NameUtils.getPropertyNameForGetter(jsonGetter.getName());
                        String n = resolveName(annotationMetadata, jsonGetterAnnotationMetadata, propertyName, false, propertyNamingStrategy);
                        final Argument<Object> returnType = jsonGetter.getReturnType().asArgument();
                        writeProperties.add(new SerProperty<>(this, n,
                                                              returnType,
                                                              jsonGetter::invoke,
                                                              findSerializer(encoderContext, returnType,
                                                                             jsonGetterAnnotationMetadata),
                                                              null,
                                                              encoderContext
                                            )
                        );
                    }
                } else {
                    writeProperties = Collections.emptyList();
                }
                this.anyGetter = anyGetterProperty;
                this.wrapperProperty = introspection.stringValue(SerdeConfig.class, SerdeConfig.WRAPPER_PROPERTY).orElse(null);
            }
        }
        initialized = true;
    }

    private PropertyNamingStrategy getPropertyNamingStrategy(AnnotationMetadata annotationMetadata,
                                                             Serializer.EncoderContext encoderContext,
                                                             PropertyNamingStrategy defaultNamingStrategy) throws SerdeException {
        Class<? extends PropertyNamingStrategy> namingStrategyClass = annotationMetadata.classValue(SerdeConfig.class, SerdeConfig.RUNTIME_NAMING)
                .orElse(null);
        return namingStrategyClass == null ? defaultNamingStrategy : encoderContext.findNamingStrategy(namingStrategyClass);
    }

    private Comparator<BeanProperty<?, Object>> getPropertyComparator() {
        return BEAN_PROPERTY_COMPARATOR;
    }

    private <K> Serializer<K> findSerializer(Serializer.EncoderContext encoderContext,
                                             Argument<K> argument,
                                             AnnotationMetadata annotationMetadata) throws SerdeException {

        Class customSer = annotationMetadata.classValue(SerdeConfig.class, SerdeConfig.SERIALIZER_CLASS).orElse(null);
        if (customSer != null) {
            return encoderContext.findCustomSerializer(customSer);
        }
        return (Serializer<K>) encoderContext.findSerializer(argument);
    }

    private String resolveName(AnnotationMetadata annotationMetadata,
                               AnnotationMetadata propertyAnnotationMetadata,
                               String defaultPropertyName,
                               boolean unwrapped,
                               PropertyNamingStrategy propertyNamingStrategy) {

        String n =
                propertyNamingStrategy == null ? propertyAnnotationMetadata.stringValue(SerdeConfig.class, SerdeConfig.PROPERTY).orElse(null) :
                        propertyNamingStrategy.translate(new AnnotatedElement() {
                            @Override
                            public String getName() {
                                return defaultPropertyName;
                            }

                            @Override
                            public AnnotationMetadata getAnnotationMetadata() {
                                return propertyAnnotationMetadata;
                            }
                        });
        if (n == null) {
            n = propertyAnnotationMetadata.stringValue(JK_PROP)
                    .orElse(defaultPropertyName);
        }
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
        public final SerBean<B> bean;
        public final String name;
        public final Argument<P> argument;
        public final Serializer<P> serializer;
        public final Class<?>[] views;
        public final String managedRef;
        public final String backRef;
        public final SerdeConfig.SerInclude include;
        private final @Nullable
        P injected;
        private final Function<B, P> reader;
        // CHECKSTYLE:ON

        public SerProperty(
                SerBean<B> bean,
                @NonNull String name,
                @NonNull Argument<P> argument,
                @Nullable Function<B, P> reader,
                @NonNull Serializer<P> serializer,
                @Nullable P injected,
                @NonNull Serializer.EncoderContext encoderContext) throws SerdeException {
           this(bean, name, argument, argument.getAnnotationMetadata(), reader, serializer, injected, encoderContext);
        }

        public SerProperty(
                SerBean<B> bean,
                @NonNull String name,
                @NonNull Argument<P> argument,
                @NonNull AnnotationMetadata annotationMetadata,
                @Nullable Function<B, P> reader,
                @NonNull Serializer<P> serializer,
                @Nullable P injected,
                @NonNull Serializer.EncoderContext encoderContext) throws SerdeException {
            this.bean = bean;
            this.name = name;
            this.argument = argument;
            this.reader = reader;
            final AnnotationMetadata beanMetadata = bean.introspection.getAnnotationMetadata();
            final AnnotationMetadata hierarchy =
                    annotationMetadata.isEmpty() ? beanMetadata : new AnnotationMetadataHierarchy(beanMetadata, annotationMetadata);
            this.serializer = serializer.createSpecific(encoderContext, argument);
            this.views = SerdeAnnotationUtil.resolveViews(beanMetadata, annotationMetadata);
            this.include = hierarchy
                    .enumValue(SerdeConfig.class, SerdeConfig.INCLUDE, SerdeConfig.SerInclude.class)
                    .orElse(bean.configuration.getInclusion());
            this.injected = injected;
            this.managedRef = annotationMetadata.stringValue(SerdeConfig.ManagedRef.class)
                                .orElse(null);
            this.backRef = annotationMetadata.stringValue(SerdeConfig.BackRef.class)
                    .orElse(null);
        }

        public P get(B bean) {
            if (injected != null) {
                return injected;
            } else {
                return reader.apply(bean);
            }
        }
    }

}
