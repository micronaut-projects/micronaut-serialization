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
import org.slf4j.LoggerFactory;

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
import java.util.function.Function;

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
    @Nullable
    public final String propertiesNamePrefix;
    @Nullable
    public final String propertiesNameSuffix;

    private volatile boolean initialized;
    private volatile boolean initializing;

    private List<Initializer> initializers = new ArrayList<>();

    // CHECKSTYLE:ON

    SerBean(Argument<T> definition,
            SerdeIntrospections introspections,
            Serializer.EncoderContext encoderContext,
            SerializationConfiguration configuration,
            @Nullable String propertiesNamePrefix,
            @Nullable String propertiesNameSuffix,
            BeanContext beanContext) throws SerdeException {
        this.propertiesNamePrefix = propertiesNamePrefix;
        this.propertiesNameSuffix = propertiesNameSuffix;
        this.configuration = configuration;
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
                        .toList();
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

                AnnotationMetadata am = new AnnotationMetadataHierarchy(introspection, definition.getAnnotationMetadata());
                Optional<String> subType = am.stringValue(SerdeConfig.class, SerdeConfig.TYPE_NAME);

                if (!properties.isEmpty() || !jsonGetters.isEmpty() || subType.isPresent()) {
                    writeProperties = new ArrayList<>(properties.size() + jsonGetters.size());
                    subType.ifPresent(typeName -> {
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
                        PropertyNamingStrategy propertyNamingStrategy = getPropertyNamingStrategy(property.getAnnotationMetadata(), encoderContext, entityPropertyNamingStrategy);

                        String resolvedPropertyName = resolveName(
                            propertyAnnotationMetadata,
                            argument.getName(),
                            propertiesNamePrefix,
                            propertiesNameSuffix,
                            propertyNamingStrategy);

                        final SerProperty<T, Object> serProperty = new PropSerProperty<>(SerBean.this,
                                resolvedPropertyName,
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

                    for (BeanMethod<T, Object> jsonGetter : jsonGetters) {
                        PropertyNamingStrategy propertyNamingStrategy = getPropertyNamingStrategy(jsonGetter.getAnnotationMetadata(), encoderContext, entityPropertyNamingStrategy);
                        final AnnotationMetadata jsonGetterAnnotationMetadata = jsonGetter.getAnnotationMetadata();
                        String resolvedPropertyName = resolveName(jsonGetterAnnotationMetadata,
                            NameUtils.getPropertyNameForGetter(jsonGetter.getName()),
                            propertiesNamePrefix,
                            propertiesNameSuffix,
                            propertyNamingStrategy);

                        final Argument<Object> returnType = jsonGetter.getReturnType().asArgument();
                        MethodSerProperty<T, Object> prop = new MethodSerProperty<>(SerBean.this, resolvedPropertyName,
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

    private <Y, Z> void initProperty(SerProperty<Y, Z> prop,
                                     Serializer.EncoderContext encoderContext) throws SerdeException {
        if (prop.serializer != null) {
            return;
        }

        Class customSer = prop.annotationMetadata.classValue(SerdeConfig.class, SerdeConfig.SERIALIZER_CLASS).orElse(null);
        Serializer<Z> serializer;
        if (customSer != null) {
            serializer = encoderContext.findCustomSerializer(customSer);
        } else {
            serializer = (Serializer<Z>) encoderContext.findSerializer(prop.argument);
        }

        if (prop.unwrapped) {
            if (serializer instanceof ObjectSerializer objectSerializer) {
                String prefix = prop.annotationMetadata.stringValue(SerdeConfig.SerUnwrapped.class, SerdeConfig.SerUnwrapped.PREFIX).orElse("");
                String suffix = prop.annotationMetadata.stringValue(SerdeConfig.SerUnwrapped.class, SerdeConfig.SerUnwrapped.SUFFIX).orElse("");
                if (propertiesNamePrefix != null) {
                    prefix = propertiesNamePrefix + prefix;
                }
                if (propertiesNameSuffix != null) {
                    suffix = suffix + propertiesNameSuffix;
                }
                prop.objectSerializer = (io.micronaut.serde.ObjectSerializer<Z>) objectSerializer.createSpecificUnwrapped(encoderContext, prop.argument, prefix, suffix);
                prop.serializer = prop.objectSerializer;
            } else {
                throw new SerdeException("Serializer for a property: " + prop.name + " doesn't support serializing into an existing object");
            }
        } else {
            prop.serializer = serializer.createSpecific(encoderContext, prop.argument);
        }

        prop.annotationMetadata = null;
    }

    private boolean isSimpleBean() {
        if (anyGetter != null) {
            return false;
        }
        if (propertyFilter != null) {
            return false;
        }
        for (SerProperty<T, Object> property : writeProperties) {
            if (property.unwrapped || property.backRef != null || property.include != SerdeConfig.SerInclude.ALWAYS || property.views != null || property.managedRef != null) {
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

    private String resolveName(AnnotationMetadata propertyAnnotationMetadata,
                               String name,
                               @Nullable
                               String prefix,
                               @Nullable
                               String suffix,
                               PropertyNamingStrategy propertyNamingStrategy) {

        String resolvedName =
                propertyNamingStrategy == null ? propertyAnnotationMetadata.stringValue(SerdeConfig.class, SerdeConfig.PROPERTY).orElse(null) :
                        propertyNamingStrategy.translate(new AnnotatedElement() {
                            @Override
                            public String getName() {
                                return name;
                            }

                            @Override
                            public AnnotationMetadata getAnnotationMetadata() {
                                return propertyAnnotationMetadata;
                            }
                        });
        if (resolvedName == null) {
            resolvedName = propertyAnnotationMetadata.stringValue(JK_PROP).orElse(name);
        }
        if (prefix != null) {
            resolvedName = prefix + resolvedName;
        }
        if (suffix != null) {
            resolvedName = resolvedName + suffix;
        }
        return resolvedName;
    }

    private PropertyFilter getPropertyFilterIfPresent(BeanContext beanContext, String typeName) {
        Optional<String> filterName = introspection.stringValue(SerdeConfig.class, SerdeConfig.FILTER);
        if (filterName.isPresent() && !filterName.get().isEmpty()) {
            return beanContext.findBean(PropertyFilter.class, Qualifiers.byName(filterName.get()))
                .orElseGet(() -> {
                    LoggerFactory.getLogger(SerBean.class)
                        .warn("Json filter with name '{}' was defined on type {} but no PropertyFilter bean with the name exists", filterName.get(), typeName);
                    return null;
                });
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
        public final boolean unwrapped;
        // Null when not initialized SerBean
        public Serializer<P> serializer;
        @Nullable
        public io.micronaut.serde.ObjectSerializer<P> objectSerializer;
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
            this.unwrapped = annotationMetadata.hasAnnotation(SerdeConfig.SerUnwrapped.class);
        }

        public abstract P get(B bean);
    }

    private interface Initializer {

        void initialize(Serializer.EncoderContext encoderContext) throws SerdeException;

    }

}
