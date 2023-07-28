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

import io.micronaut.context.BeanContext;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.core.annotation.AnnotatedElement;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanMethod;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.UnsafeBeanProperty;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.serde.PropertyFilter;
import io.micronaut.serde.SerdeIntrospections;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.SerializationConfiguration;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.util.SerdeAnnotationUtil;

import java.lang.reflect.Modifier;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    public SerProperty<T, Object> anyGetter;
    public SerProperty<T, Object> jsonValue;
    public final SerializationConfiguration configuration;
    public final boolean simpleBean;
    public final boolean subtyped;
    public final PropertyFilter propertyFilter;

    private volatile boolean initialized;
    private volatile boolean initializing;

    private List<Initializer> initializers = new ArrayList<>();

    // CHECKSTYLE:ON

    SerBean(Argument<T> definition,
            SerdeIntrospections introspections,
            Serializer.EncoderContext encoderContext,
            SerializationConfiguration configuration,
            BeanContext beanContext) throws SerdeException {
        this.configuration = configuration;
        final AnnotationMetadata annotationMetadata = definition.getAnnotationMetadata();
        this.introspection = introspections.getSerializableIntrospection(definition);
        this.propertyFilter = getPropertyFilterIfPresent(beanContext, definition.getSimpleName());
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
            jsonValue = new PropSerProperty<>(
                    SerBean.this,
                    beanProperty.getName(),
                    serType,
                    propertyAnnotationMetadata,
                    beanProperty
            );
            initializers.add(ctx -> initProperty(SerBean.this.jsonValue, ctx));
            writeProperties = Collections.emptyList();
        } else {
            final Collection<BeanMethod<T, Object>> beanMethods = introspection.getBeanMethods();
            final BeanMethod<T, Object> serMethod = beanMethods.stream()
                    .filter(m -> m.isAnnotationPresent(SerdeConfig.SerValue.class) || m.getAnnotationMetadata().hasAnnotation(JACKSON_VALUE))
                    .findFirst().orElse(null);
            if (serMethod != null) {
                wrapperProperty = null;
                final Argument<Object> serType = serMethod.getReturnType().asArgument();
                jsonValue = new MethodSerProperty<>(
                        SerBean.this,
                        serMethod.getName(),
                        serType,
                        serMethod.getAnnotationMetadata(),
                        serMethod
                );
                initializers.add(ctx -> initProperty(SerBean.this.jsonValue, ctx));
                writeProperties = Collections.emptyList();
            } else {
                final List<BeanMethod<T, Object>> jsonGetters = new ArrayList<>(beanMethods.size());
                BeanMethod<T, Object> anyGetter = null;
                for (BeanMethod<T, Object> beanMethod : beanMethods) {
                    if (beanMethod.isAnnotationPresent(SerdeConfig.SerGetter.class)) {
                        jsonGetters.add(beanMethod);
                    } else if (beanMethod.isAnnotationPresent(SerdeConfig.SerAnyGetter.class)) {
                        anyGetter = beanMethod;
                    }
                }
                this.anyGetter = anyGetter != null ? new MethodSerProperty<>(SerBean.this, "any",
                        anyGetter.getReturnType().asArgument(),
                        anyGetter.getAnnotationMetadata(),
                        anyGetter
                ) : null;

                SerProperty<T, Object> ag = this.anyGetter;
                if (ag != null) {
                    initializers.add(ctx -> initProperty(ag, ctx));
                }

                if (!properties.isEmpty() || !jsonGetters.isEmpty()) {
                    writeProperties = new ArrayList<>(properties.size() + jsonGetters.size());
                    AnnotationMetadata am = new AnnotationMetadataHierarchy(introspection, definition.getAnnotationMetadata());
                    am.stringValue(SerdeConfig.class, SerdeConfig.TYPE_NAME).ifPresent(typeName -> {
                        String typeProperty = am.stringValue(SerdeConfig.class, SerdeConfig.TYPE_PROPERTY).orElse(null);
                        if (typeProperty != null) {
                            SerProperty<T, String> prop;
                            if (SerdeConfig.TYPE_NAME_CLASS_SIMPLE_NAME_PLACEHOLDER.equals(typeName)) {
                                prop = new CustomSerProperty<>(SerBean.this,
                                        typeProperty,
                                        Argument.of(String.class, typeProperty),
                                        t -> t.getClass().getSimpleName());
                            } else {
                                prop = new InjectedSerProperty<>(SerBean.this,
                                        typeProperty,
                                        Argument.of(String.class, typeProperty),
                                        typeName);
                            }
                            writeProperties.add((SerProperty) prop);
                            initializers.add(context -> {
                                try {
                                    initProperty(prop, context);
                                } catch (SerdeException e) {
                                    throw new IntrospectionException("Error configuring subtype binding for type " + introspection.getBeanType() + ": " + e.getMessage());
                                }
                            });
                        }
                    });
                    for (Map.Entry<BeanProperty<T, Object>, AnnotationMetadata> propWithAnnotations : properties) {
                        final BeanProperty<T, Object> property = propWithAnnotations.getKey();
                        final Argument<Object> argument = property.asArgument();
                        final AnnotationMetadata propertyAnnotationMetadata = propWithAnnotations.getValue();
                        final String defaultPropertyName = argument.getName();
                        boolean unwrapped = propertyAnnotationMetadata.hasAnnotation(SerdeConfig.SerUnwrapped.class);
                        PropertyNamingStrategy propertyNamingStrategy = getPropertyNamingStrategy(property.getAnnotationMetadata(), encoderContext, entityPropertyNamingStrategy);
                        if (unwrapped) {
                            processUnwrapped(
                                introspections,
                                property,
                                argument,
                                propertyAnnotationMetadata,
                                propertyNamingStrategy,
                                null
                            );
                        } else {
                            String n = resolveName(annotationMetadata, propertyAnnotationMetadata, defaultPropertyName, false, propertyNamingStrategy);
                            final SerProperty<T, Object> serProperty = new PropSerProperty<>(SerBean.this,
                                    n,
                                    argument,
                                    propertyAnnotationMetadata,
                                    property
                            );

                            initializers.add(ctx -> {
                                try {
                                    initProperty(serProperty, ctx);
                                } catch (SerdeException e) {
                                    throw new SerdeException("Error resolving serializer for property [" + property + "] of type [" + argument.getType().getName() + "]: " + e.getMessage(), e);
                                }
                            });

                            if (propertyAnnotationMetadata.hasDeclaredAnnotation(SerdeConfig.SerAnyGetter.class)) {
                                this.anyGetter = serProperty;
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
                        MethodSerProperty<T, Object> prop = new MethodSerProperty<>(SerBean.this, n,
                                returnType,
                                jsonGetterAnnotationMetadata,
                                jsonGetter
                        );
                        writeProperties.add(prop);
                        initializers.add(ctx -> initProperty(prop, ctx));
                    }
                } else {
                    writeProperties = Collections.emptyList();
                }
                this.wrapperProperty = introspection.stringValue(SerdeConfig.class, SerdeConfig.WRAPPER_PROPERTY).orElse(null);
            }
        }
        simpleBean = isSimpleBean();
        boolean isAbstractIntrospection = Modifier.isAbstract(introspection.getBeanType().getModifiers());
        subtyped = isAbstractIntrospection || introspection.getAnnotationMetadata().hasDeclaredAnnotation(SerdeConfig.SerSubtyped.class);
    }

    private void processUnwrapped(
        SerdeIntrospections introspections,
        BeanProperty<T, Object> property,
        Argument<Object> argument,
        AnnotationMetadata propertyAnnotationMetadata,
        PropertyNamingStrategy propertyNamingStrategy,
        Function<T, Object> nestedValueResolver) {
        BeanIntrospection<Object> propertyIntrospection = introspections.getSerializableIntrospection(property.asArgument());
        Set<String> ignoredProperties = Arrays.stream(argument.getAnnotationMetadata().stringValues(SerdeConfig.SerIgnored.class)).collect(Collectors.toSet());
        for (BeanProperty<Object, Object> unwrappedProperty : propertyIntrospection.getBeanProperties()) {
            if (!ignoredProperties.contains(unwrappedProperty.getName())) {
                Argument<Object> unwrappedPropertyArgument = unwrappedProperty.asArgument();
                AnnotationMetadata unwrappedPropertyAnnotationMetadata = unwrappedProperty.getAnnotationMetadata();
                Function<T, Object> valueResolver;

                if (nestedValueResolver != null) {
                    valueResolver = bean -> unwrappedProperty.get(nestedValueResolver.apply(bean));
                } else {
                    valueResolver = bean -> unwrappedProperty.get(property.get(bean));
                }

                String n = resolveName(propertyAnnotationMetadata,
                    unwrappedPropertyAnnotationMetadata,
                    unwrappedPropertyArgument.getName(),
                    true, propertyNamingStrategy);
                final AnnotationMetadataHierarchy combinedMetadata =
                    new AnnotationMetadataHierarchy(
                        argument.getAnnotationMetadata(),
                        unwrappedPropertyAnnotationMetadata
                    );

                if (unwrappedPropertyAnnotationMetadata.hasDeclaredAnnotation(SerdeConfig.SerUnwrapped.class)) {
                    // nested unwrapped
                    processUnwrapped(
                        introspections,
                        (BeanProperty<T, Object>) unwrappedProperty,
                        unwrappedPropertyArgument,
                        combinedMetadata,
                        propertyNamingStrategy,
                        valueResolver
                    );
                } else {

                    if (!combinedMetadata.booleanValue(SerdeConfig.class, SerdeConfig.IGNORED).orElse(false) &&
                        !combinedMetadata.booleanValue(SerdeConfig.class, SerdeConfig.READ_ONLY).orElse(false)) {

                        CustomSerProperty<T, Object> prop = new CustomSerProperty<>(SerBean.this, n,
                            unwrappedPropertyArgument,
                            combinedMetadata,
                            valueResolver
                        );
                        writeProperties.add(prop);
                        initializers.add(ctx -> initProperty(prop, ctx));
                    }
                }
            }
        }
    }

    public void initialize(Serializer.EncoderContext encoderContext) throws SerdeException {
        if (!initialized) {
            synchronized (this) {
                if (!initialized && !initializing) {
                    initializing = true;
                    for (Initializer initializer : initializers) {
                        initializer.initialize(encoderContext);
                    }
                    initializers = null;
                    initialized = true;
                    initializing = false;
                }
            }
        }
    }

    private <Y, Z> void initProperty(SerProperty<Y, Z> prop, Serializer.EncoderContext encoderContext) throws SerdeException {
        if (prop.serializer != null) {
            return;
        }
        prop.serializer = findSerializer(encoderContext, prop.argument, prop.annotationMetadata);
        prop.annotationMetadata = null;
    }

    private boolean isSimpleBean() {
        if (wrapperProperty != null || anyGetter != null) {
            return false;
        }
        if (propertyFilter != null) {
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
        return jsonValue != null;
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
            @NonNull String[] prefixes = annotationMetadata.stringValues(SerdeConfig.SerUnwrapped.class, SerdeConfig.SerUnwrapped.PREFIX);
            @NonNull String[] suffixes = annotationMetadata.stringValues(SerdeConfig.SerUnwrapped.class, SerdeConfig.SerUnwrapped.SUFFIX);
            if (ArrayUtils.isNotEmpty(prefixes) || ArrayUtils.isNotEmpty(suffixes)) {
                List<@NonNull String> prefixList = Arrays.asList(prefixes);
                Collections.reverse(prefixList);
                List<@NonNull String> suffixList = Arrays.asList(suffixes);
                return String.join("", prefixList) + n + String.join("", suffixList);
            }
        }
        return n;
    }

    private PropertyFilter getPropertyFilterIfPresent(BeanContext beanContext, String typeName) {
        Optional<String> filterName = introspection.stringValue(SerdeConfig.class, SerdeConfig.FILTER);
        if (filterName.isPresent() && !filterName.get().isEmpty()) {
            try {
                return beanContext.getBean(PropertyFilter.class, Qualifiers.byName(filterName.get()));
            } catch (NoSuchBeanException e) {
                throw new ConfigurationException("Json filter with name '" + filterName.get() + "' was defined on type " +
                    typeName + " but no PropertyFilter with the name exists");
            }
        }
        return null;
    }

    static final class PropSerProperty<B, P> extends SerProperty<B, P> {

        private final UnsafeBeanProperty<B, P> beanProperty;

        public PropSerProperty(SerBean<B> bean, String name, Argument<P> argument, AnnotationMetadata annotationMetadata, BeanProperty<B, P> beanProperty) {
            super(bean, name, argument, annotationMetadata);
            this.beanProperty = (UnsafeBeanProperty<B, P>) beanProperty;
        }

        @Override
        public P get(B bean) {
            return beanProperty.getUnsafe(bean);
        }
    }

    static final class MethodSerProperty<B, P> extends SerProperty<B, P> {

        private final BeanMethod<B, P> beanMethod;

        public MethodSerProperty(SerBean<B> bean, String name, Argument<P> argument, AnnotationMetadata annotationMetadata, BeanMethod<B, P> beanMethod) {
            super(bean, name, argument, annotationMetadata);
            this.beanMethod = beanMethod;
        }

        @Override
        public P get(B bean) {
            return beanMethod.invoke(bean);
        }
    }

    static final class CustomSerProperty<B, P> extends SerProperty<B, P> {

        private final Function<B, P> reader;

        public CustomSerProperty(SerBean<B> bean, String name, Argument<P> argument, Function<B, P> reader) {
            super(bean, name, argument);
            this.reader = reader;
        }

        public CustomSerProperty(SerBean<B> bean, String name, Argument<P> argument, AnnotationMetadata annotationMetadata, Function<B, P> reader) {
            super(bean, name, argument, annotationMetadata);
            this.reader = reader;
        }

        @Override
        public P get(B bean) {
            return reader.apply(bean);
        }
    }

    static final class InjectedSerProperty<B, P> extends SerProperty<B, P> {

        private final P injected;

        public InjectedSerProperty(SerBean<B> bean, String name, Argument<P> argument, P injected) {
            super(bean, name, argument);
            this.injected = injected;
        }

        @Override
        public P get(B bean) {
            return injected;
        }
    }

    @Internal
    abstract static class SerProperty<B, P> {
        // CHECKSTYLE:OFF
        public final String name;
        public final Argument<P> argument;
        public final Class<?>[] views;
        public final String managedRef;
        public final String backRef;
        public final SerdeConfig.SerInclude include;
        // Null when not initialized SerBean
        public Serializer<P> serializer;
        public AnnotationMetadata annotationMetadata;
        // CHECKSTYLE:ON

        public SerProperty(
                SerBean<B> bean,
                @NonNull String name,
                @NonNull Argument<P> argument) {
            this(bean, name, argument, argument.getAnnotationMetadata());
        }

        public SerProperty(
                SerBean<B> bean,
                @NonNull String name,
                @NonNull Argument<P> argument,
                @NonNull AnnotationMetadata annotationMetadata) {
            this.name = name;
            this.argument = argument;
            final AnnotationMetadata beanMetadata = bean.introspection.getAnnotationMetadata();
            final AnnotationMetadata hierarchy =
                    annotationMetadata.isEmpty() ? beanMetadata : new AnnotationMetadataHierarchy(beanMetadata, annotationMetadata);
            this.views = SerdeAnnotationUtil.resolveViews(beanMetadata, annotationMetadata);
            this.include = hierarchy
                    .enumValue(SerdeConfig.class, SerdeConfig.INCLUDE, SerdeConfig.SerInclude.class)
                    .orElse(bean.configuration.getInclusion());
            this.managedRef = annotationMetadata.stringValue(SerdeConfig.SerManagedRef.class)
                    .orElse(null);
            this.backRef = annotationMetadata.stringValue(SerdeConfig.SerBackRef.class)
                    .orElse(null);
            this.annotationMetadata = annotationMetadata;
        }

        public abstract P get(B bean);
    }

    private interface Initializer {

        void initialize(Serializer.EncoderContext encoderContext) throws SerdeException;

    }

}
