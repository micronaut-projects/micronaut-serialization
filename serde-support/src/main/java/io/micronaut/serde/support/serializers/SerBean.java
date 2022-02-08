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
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.util.SerdeAnnotationUtil;

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
    public List<SerProperty<T, Object>> writeProperties;
    @Nullable
    public final String wrapperProperty;
    @Nullable
    public SerProperty<T, Object> anyGetter;
    public SerProperty<T, Object> jsonValue;
    public final SerializationConfiguration configuration;

    private List<Initializer<SerProperty<T, Object>>> writePropertiesInitializer;
    private Initializer<SerProperty<T, Object>> jsonValueInitializer;
    private Initializer<SerProperty<T, Object>> anyGetterInitializer;
    public volatile boolean initialized;
    public boolean simpleBean;

    // CHECKSTYLE:ON

    SerBean(Argument<T> definition,
            SerdeIntrospections introspections,
            Serializer.EncoderContext encoderContext,
            SerializationConfiguration configuration) throws SerdeException {
        //noinspection unchecked
        this.configuration = configuration;
        final AnnotationMetadata annotationMetadata = definition.getAnnotationMetadata();
        this.introspection = introspections.getSerializableIntrospection(definition);
        PropertyNamingStrategy entityPropertyNamingStrategy = getPropertyNamingStrategy(introspection, encoderContext, null);
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
            BeanProperty<T, Object> beanProperty = serPropEntry.getKey();
            final Argument<Object> serType = beanProperty.asArgument();
            AnnotationMetadata propertyAnnotationMetadata = serPropEntry.getValue();
            jsonValueInitializer = () -> new PropSerProperty<>(
                    SerBean.this,
                    beanProperty.getName(),
                    serType,
                    propertyAnnotationMetadata,
                    findSerializer(encoderContext, serType, propertyAnnotationMetadata),
                    encoderContext,
                    beanProperty
            );
        } else {
            final Collection<BeanMethod<T, Object>> beanMethods = introspection.getBeanMethods();
            final BeanMethod<T, Object> serMethod = beanMethods.stream()
                    .filter(m -> m.isAnnotationPresent(SerdeConfig.SerValue.class) || m.getAnnotationMetadata().hasAnnotation(JACKSON_VALUE))
                    .findFirst().orElse(null);
            if (serMethod != null) {
                wrapperProperty = null;
                final Argument<Object> serType = serMethod.getReturnType().asArgument();
                jsonValueInitializer = () -> new MethodSerProperty<>(
                        SerBean.this,
                        serMethod.getName(),
                        serType,
                        findSerializer(encoderContext, serType, serMethod.getAnnotationMetadata()),
                        encoderContext,
                        serMethod
                );
            } else {
                final List<BeanMethod<T, Object>> jsonGetters = new ArrayList<>(beanMethods.size());
                BeanMethod<T, Object> anyGetter = null;
                for (BeanMethod<T, Object> beanMethod : beanMethods) {
                    if (beanMethod.isAnnotationPresent(SerdeConfig.Getter.class)) {
                        jsonGetters.add(beanMethod);
                    } else if (beanMethod.isAnnotationPresent(SerdeConfig.AnyGetter.class)) {
                        anyGetter = beanMethod;
                    }
                }
                BeanMethod<T, Object> finalAnyGetter = anyGetter;
                Initializer<SerProperty<T, Object>> anyGetterPropertyInitializer = anyGetter != null ? () -> new MethodSerProperty<>(SerBean.this, "any",
                        finalAnyGetter.getReturnType().asArgument(),
                        findSerializer(encoderContext, finalAnyGetter.getReturnType().asArgument(), finalAnyGetter.getAnnotationMetadata()),
                        encoderContext,
                        finalAnyGetter
                ) : null;

                if (!properties.isEmpty() || !jsonGetters.isEmpty()) {
                    writePropertiesInitializer = new ArrayList<>(properties.size() + jsonGetters.size());
                    AnnotationMetadata am = new AnnotationMetadataHierarchy(introspection, definition.getAnnotationMetadata());
                    am.stringValue(SerdeConfig.class, SerdeConfig.TYPE_NAME).ifPresent((typeName) -> {
                        String typeProperty = am.stringValue(SerdeConfig.class, SerdeConfig.TYPE_PROPERTY).orElse(null);
                        if (typeProperty != null) {
                            Initializer<SerProperty<T, String>> propertyInitializer;
                            if (SerdeConfig.TYPE_NAME_CLASS_SIMPLE_NAME_PLACEHOLDER.equals(typeName)) {
                                propertyInitializer = () -> {
                                    try {
                                        return new CustomSerProperty<>(SerBean.this,
                                                typeProperty,
                                                Argument.of(String.class, typeProperty),
                                                (Serializer<String>) encoderContext.findSerializer(Argument.STRING),
                                                encoderContext,
                                                t -> t.getClass().getSimpleName());
                                    } catch (SerdeException e) {
                                        throw new IntrospectionException("Error configuring subtype binding for type " + introspection.getBeanType() + ": " + e.getMessage());
                                    }
                                };
                            } else {
                                propertyInitializer = () -> {
                                    try {
                                        return new InjectedSerProperty<>(SerBean.this,
                                                typeProperty,
                                                Argument.of(String.class, typeProperty),
                                                (Serializer<String>) encoderContext.findSerializer(Argument.STRING),
                                                encoderContext,
                                                typeName);
                                    } catch (SerdeException e) {
                                        throw new IntrospectionException("Error configuring subtype binding for type " + introspection.getBeanType() + ": " + e.getMessage());
                                    }
                                };
                            }
                            writePropertiesInitializer.add((Initializer) propertyInitializer);
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
                                writePropertiesInitializer.add(() -> new CustomSerProperty<>(SerBean.this, n,
                                        unwrappedPropertyArgument,
                                        combinedMetadata,
                                        findSerializer(encoderContext, unwrappedPropertyArgument, argument.getAnnotationMetadata()),
                                        encoderContext,
                                        bean -> unwrappedProperty.get(property.get(bean))
                                ));
                            }
                        } else {
                            String n = resolveName(annotationMetadata, propertyAnnotationMetadata, defaultPropertyName, false, propertyNamingStrategy);
                            final Initializer<SerProperty<T, Object>> serProperty;
//                            try {
                            serProperty = () -> {
                                try {
                                    return new PropSerProperty<>(SerBean.this,
                                            n,
                                            argument,
                                            findSerializer(encoderContext, argument, propertyAnnotationMetadata),
                                            encoderContext,
                                            property
                                    );
                                } catch (SerdeException e) {
                                    throw new SerdeException("Error resolving serializer for property [" + property + "] of type [" + argument.getType().getName() + "]: " + e.getMessage(), e);
                                }
                            };

                            if (propertyAnnotationMetadata.hasDeclaredAnnotation(SerdeConfig.AnyGetter.class)) {
                                anyGetterPropertyInitializer = serProperty;
                            } else {
                                writePropertiesInitializer.add(serProperty);
                            }
                        }
                    }

                    for (BeanMethod<T, Object> jsonGetter : jsonGetters) {
                        PropertyNamingStrategy propertyNamingStrategy = getPropertyNamingStrategy(jsonGetter.getAnnotationMetadata(), encoderContext, entityPropertyNamingStrategy);
                        final AnnotationMetadata jsonGetterAnnotationMetadata = jsonGetter.getAnnotationMetadata();
                        final String propertyName = NameUtils.getPropertyNameForGetter(jsonGetter.getName());
                        String n = resolveName(annotationMetadata, jsonGetterAnnotationMetadata, propertyName, false, propertyNamingStrategy);
                        final Argument<Object> returnType = jsonGetter.getReturnType().asArgument();
                        writePropertiesInitializer.add(
                                () -> new MethodSerProperty<>(SerBean.this, n,
                                        returnType,
                                        findSerializer(encoderContext, returnType, jsonGetterAnnotationMetadata),
                                        encoderContext,
                                        jsonGetter
                                )
                        );
                    }
                }
                this.anyGetterInitializer = anyGetterPropertyInitializer;
                this.wrapperProperty = introspection.stringValue(SerdeConfig.class, SerdeConfig.WRAPPER_PROPERTY).orElse(null);
            }
        }
    }

    public void initialize() throws SerdeException {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    if (writePropertiesInitializer == null) {
                        writeProperties = Collections.emptyList();
                    } else {
                        writeProperties = new ArrayList<>(writePropertiesInitializer.size());
                        for (Initializer<SerProperty<T, Object>> i : writePropertiesInitializer) {
                            writeProperties.add(i.initialize());
                        }
                        writePropertiesInitializer = null;
                    }
                    if (anyGetterInitializer != null) {
                        anyGetter = anyGetterInitializer.initialize();
                        anyGetterInitializer = null;
                    }
                    if (jsonValueInitializer != null) {
                        jsonValue = jsonValueInitializer.initialize();
                        jsonValueInitializer = null;
                    }
                    simpleBean = isSimpleBean();
                    initialized = true;
                }
            }
        }
    }

    private boolean isSimpleBean() {
        if (wrapperProperty != null || anyGetter != null) {
            return false;
        }
        for (SerProperty<T, Object> property : writeProperties) {
            if (property.backRef != null || property.include != SerdeConfig.SerInclude.ALWAYS || property.views != null || property.managedRef != null) {
                return false;
            }
        }
        return true;
    }

    public boolean hasJsonValue() {
        return jsonValueInitializer != null || jsonValue != null;
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
            return encoderContext.findCustomSerializer(customSer).createSpecific(encoderContext, argument);
        }
        return (Serializer<K>) encoderContext.findSerializer(argument).createSpecific(encoderContext, argument);
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

    static final class PropSerProperty<B, P> extends SerProperty<B, P> {

        private final BeanProperty<B, P> beanProperty;

        public PropSerProperty(SerBean<B> bean, String name, Argument<P> argument, Serializer<P> serializer, Serializer.EncoderContext encoderContext, BeanProperty<B, P> beanProperty) throws SerdeException {
            super(bean, name, argument, serializer, encoderContext);
            this.beanProperty = beanProperty;
        }

        public PropSerProperty(SerBean<B> bean, String name, Argument<P> argument, AnnotationMetadata annotationMetadata, Serializer<P> serializer, Serializer.EncoderContext encoderContext, BeanProperty<B, P> beanProperty) throws SerdeException {
            super(bean, name, argument, annotationMetadata, serializer, encoderContext);
            this.beanProperty = beanProperty;
        }

        @Override
        public P get(B bean) {
            return beanProperty.get(bean);
        }
    }

    static final class MethodSerProperty<B, P> extends SerProperty<B, P> {

        private final BeanMethod<B, P> beanMethod;

        public MethodSerProperty(SerBean<B> bean, String name, Argument<P> argument, Serializer<P> serializer, Serializer.EncoderContext encoderContext, BeanMethod<B, P> beanMethod) throws SerdeException {
            super(bean, name, argument, serializer, encoderContext);
            this.beanMethod = beanMethod;
        }

        @Override
        public P get(B bean) {
            return beanMethod.invoke(bean);
        }
    }

    static final class CustomSerProperty<B, P> extends SerProperty<B, P> {

        private final Function<B, P> reader;

        public CustomSerProperty(SerBean<B> bean, String name, Argument<P> argument, Serializer<P> serializer, Serializer.EncoderContext encoderContext, Function<B, P> reader) throws SerdeException {
            super(bean, name, argument, serializer, encoderContext);
            this.reader = reader;
        }

        public CustomSerProperty(SerBean<B> bean, String name, Argument<P> argument, AnnotationMetadata annotationMetadata, Serializer<P> serializer, Serializer.EncoderContext encoderContext, Function<B, P> reader) throws SerdeException {
            super(bean, name, argument, annotationMetadata, serializer, encoderContext);
            this.reader = reader;
        }

        @Override
        public P get(B bean) {
            return reader.apply(bean);
        }
    }

    static final class InjectedSerProperty<B, P> extends SerProperty<B, P> {

        private final P injected;

        public InjectedSerProperty(SerBean<B> bean, String name, Argument<P> argument, Serializer<P> serializer, Serializer.EncoderContext encoderContext, P injected) throws SerdeException {
            super(bean, name, argument, serializer, encoderContext);
            this.injected = injected;
        }

        @Override
        public P get(B bean) {
            return injected;
        }
    }

    @Internal
    static abstract class SerProperty<B, P> {
        // CHECKSTYLE:OFF
        public final SerBean<B> bean;
        public final String name;
        public final Argument<P> argument;
        public final Serializer<P> serializer;
        public final Class<?>[] views;
        public final String managedRef;
        public final String backRef;
        public final SerdeConfig.SerInclude include;
        // CHECKSTYLE:ON

        public SerProperty(
                SerBean<B> bean,
                @NonNull String name,
                @NonNull Argument<P> argument,
                @NonNull Serializer<P> serializer,
                @NonNull Serializer.EncoderContext encoderContext) throws SerdeException {
            this(bean, name, argument, argument.getAnnotationMetadata(), serializer, encoderContext);
        }

        public SerProperty(
                SerBean<B> bean,
                @NonNull String name,
                @NonNull Argument<P> argument,
                @NonNull AnnotationMetadata annotationMetadata,
                @NonNull Serializer<P> serializer,
                @NonNull Serializer.EncoderContext encoderContext) throws SerdeException {
            this.bean = bean;
            this.name = name;
            this.argument = argument;
            final AnnotationMetadata beanMetadata = bean.introspection.getAnnotationMetadata();
            final AnnotationMetadata hierarchy =
                    annotationMetadata.isEmpty() ? beanMetadata : new AnnotationMetadataHierarchy(beanMetadata, annotationMetadata);
            this.serializer = serializer.createSpecific(encoderContext, argument);
            this.views = SerdeAnnotationUtil.resolveViews(beanMetadata, annotationMetadata);
            this.include = hierarchy
                    .enumValue(SerdeConfig.class, SerdeConfig.INCLUDE, SerdeConfig.SerInclude.class)
                    .orElse(bean.configuration.getInclusion());
            this.managedRef = annotationMetadata.stringValue(SerdeConfig.ManagedRef.class)
                    .orElse(null);
            this.backRef = annotationMetadata.stringValue(SerdeConfig.BackRef.class)
                    .orElse(null);
        }

        public abstract P get(B bean);
    }

    private interface Initializer<T> {

        T initialize() throws SerdeException;

    }

}
