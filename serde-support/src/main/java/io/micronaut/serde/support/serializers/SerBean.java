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
import io.micronaut.core.beans.BeanReadProperty;
import io.micronaut.core.beans.UnsafeBeanReadProperty;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.serde.PropertyFilter;
import io.micronaut.serde.SerdeIntrospections;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.SerializationConfiguration;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.util.SerdeAnnotationUtil;
import io.micronaut.serde.support.util.SerdeArgumentConf;
import io.micronaut.serde.support.util.SubtypeInfo;
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
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Predicate;

@Internal
final class SerBean<T> {
    private static final Comparator<BeanReadProperty<?, Object>> BEAN_PROPERTY_COMPARATOR = (o1, o2) -> OrderUtil.COMPARATOR.compare(
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
    public final String arrayWrapperProperty;
    @Nullable
    public SerProperty<T, Object> jsonValue;
    public final SerializationConfiguration configuration;
    public final boolean simpleBean;
    public final boolean subtyped;
    public final PropertyFilter propertyFilter;
    public final SubtypeInfo subtypeInfo;
    @Nullable
    private final SerdeArgumentConf serdeArgumentConf;

    private volatile boolean initialized;
    private volatile boolean initializing;

    private List<Initializer> initializers = new ArrayList<>();

    // CHECKSTYLE:ON

    SerBean(Argument<T> type,
            SerdeIntrospections introspections,
            Serializer.EncoderContext encoderContext,
            @Nullable SerdeArgumentConf serdeArgumentConf,
            SerializationConfiguration serializationConfiguration,
            @Nullable BeanContext beanContext) throws SerdeException {
        // !!! Avoid accessing annotations from the argument, the annotations are not included in the cache key
        this.serdeArgumentConf = serdeArgumentConf;
        this.configuration = encoderContext.getSerializationConfiguration().orElse(serializationConfiguration);
        this.introspection = introspections.getSerializableIntrospection(type);
        this.propertyFilter = getPropertyFilterIfPresent(beanContext, type.getSimpleName());
        subtypeInfo = serdeArgumentConf == null ? null : serdeArgumentConf.getSubtypeInfo();

        boolean allowIgnoredProperties = introspection.booleanValue(SerdeConfig.SerIgnored.class, SerdeConfig.SerIgnored.ALLOW_SERIALIZE).orElse(false);

        @Nullable
        Predicate<String> argumentPropertyPredicate = serdeArgumentConf == null ? null : serdeArgumentConf.resolveAllowPropertyPredicate(allowIgnoredProperties);

        PropertyNamingStrategy defaultPropertyNamingStrategy = encoderContext.getSerdeConfiguration().map(SerdeConfiguration::getPropertyNamingStrategy).orElse(null);
        PropertyNamingStrategy entityPropertyNamingStrategy = getPropertyNamingStrategy(introspection, encoderContext, defaultPropertyNamingStrategy);
        final Collection<Map.Entry<BeanReadProperty<T, Object>, AnnotationMetadata>> properties =
                introspection.getBeanReadProperties().stream()
                        .filter(this::filterProperty)
                        .sorted(BEAN_PROPERTY_COMPARATOR)
                        .map(beanProperty -> {
                            Optional<Argument<?>> constructorArgument = Arrays.stream(introspection.getConstructor().getArguments())
                                    .filter(a -> a.getName().equals(beanProperty.getName()) && a.getType().equals(beanProperty.getType()))
                                    .findFirst();
                            return constructorArgument.<Map.Entry<BeanReadProperty<T, Object>, AnnotationMetadata>>map(argument -> new AbstractMap.SimpleEntry<>(
                                    beanProperty,
                                    new AnnotationMetadataHierarchy(argument.getAnnotationMetadata(), beanProperty.getAnnotationMetadata())
                            )).orElseGet(() -> new AbstractMap.SimpleEntry<>(
                                    beanProperty,
                                    beanProperty.getAnnotationMetadata()
                            ));
                        })
                        .toList();
        final Map.Entry<BeanReadProperty<T, Object>, AnnotationMetadata> serPropEntry = properties.stream()
                .filter(bp -> bp.getValue().hasAnnotation(SerdeConfig.SerValue.class) || bp.getValue().hasAnnotation(JACKSON_VALUE))
                .findFirst().orElse(null);
        if (serPropEntry != null) {
            wrapperProperty = null;
            BeanReadProperty<T, Object> beanProperty = serPropEntry.getKey();
            final Argument<Object> serType = beanProperty.asArgument();
            AnnotationMetadata propertyAnnotationMetadata = serPropEntry.getValue();
            jsonValue = new PropSerProperty<>(
                SerBean.this,
                beanProperty.getName(),
                beanProperty.getName(),
                serType,
                propertyAnnotationMetadata,
                beanProperty
            );
            initializers.add(ctx -> initProperty(SerBean.this.jsonValue, ctx));
            writeProperties = Collections.emptyList();
        } else {

            final BeanMethod<T, Object> serMethod = introspection.getBeanMethods().stream()
                    .filter(m -> m.isAnnotationPresent(SerdeConfig.SerValue.class) || m.getAnnotationMetadata().hasAnnotation(JACKSON_VALUE))
                    .findFirst().orElse(null);
            if (serMethod != null) {
                wrapperProperty = null;
                jsonValue = new MethodSerProperty<>(
                    SerBean.this,
                    serMethod.getName(),
                    serMethod.getName(),
                    serMethod.getReturnType().asArgument(),
                    serMethod.getAnnotationMetadata(),
                    serMethod
                );
                initializers.add(ctx -> initProperty(SerBean.this.jsonValue, ctx));
                writeProperties = Collections.emptyList();
            } else {
                AnnotationMetadata annotationMetadata = new AnnotationMetadataHierarchy(introspection, type.getAnnotationMetadata());

                final List<BeanMethod<T, Object>> jsonGetters = new ArrayList<>(introspection.getBeanMethods().size());
                for (BeanMethod<T, Object> beanMethod : introspection.getBeanMethods()) {
                    if (beanMethod.isAnnotationPresent(SerdeConfig.SerGetter.class)
                        || beanMethod.isAnnotationPresent(SerdeConfig.SerAnyGetter.class)) {
                        jsonGetters.add(beanMethod);
                    }
                }

                Set<String> addedProperties = CollectionUtils.newHashSet(properties.size());
                PropertySubtypeDescriptor propertySubtypeDescriptor = findDescriptor(subtypeInfo, annotationMetadata, type);

                if (!properties.isEmpty() || !jsonGetters.isEmpty() || propertySubtypeDescriptor != null) {
                    writeProperties = new ArrayList<>(properties.size() + jsonGetters.size());
                    if (propertySubtypeDescriptor != null) {
                        SerProperty<T, String> prop;
                        String propertyName = propertySubtypeDescriptor.name;
                        if (SerdeConfig.TYPE_NAME_CLASS_SIMPLE_NAME_PLACEHOLDER.equals(propertySubtypeDescriptor.value)) {
                            prop = new CustomSerProperty<>(SerBean.this,
                                propertyName,
                                Argument.of(String.class, propertyName),
                                t -> t.getClass().getSimpleName());
                        } else {
                            prop = new InjectedSerProperty<>(SerBean.this,
                                propertyName,
                                Argument.of(String.class, propertyName),
                                propertySubtypeDescriptor.value);
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
                    for (Map.Entry<BeanReadProperty<T, Object>, AnnotationMetadata> propWithAnnotations : properties) {
                        final BeanReadProperty<T, Object> property = propWithAnnotations.getKey();
                        final Argument<Object> argument = property.asArgument();
                        final AnnotationMetadata propertyAnnotationMetadata = propWithAnnotations.getValue();
                        PropertyNamingStrategy propertyNamingStrategy = getPropertyNamingStrategy(property.getAnnotationMetadata(), encoderContext, entityPropertyNamingStrategy);

                        SubtypeInfo propSubtypeInfo = SubtypeInfo.createForProperty(propertyAnnotationMetadata);
                        if (propSubtypeInfo != null && propSubtypeInfo.discriminatorType() == SerdeConfig.SerSubtyped.DiscriminatorType.EXTERNAL_PROPERTY) {
                            final CustomSerProperty<T, Object> serProperty = new CustomSerProperty<>(
                                SerBean.this,
                                propSubtypeInfo.discriminatorName(),
                                (Argument) Argument.STRING,
                                bean -> {
                                    Object subtypeValue = property.get(bean);
                                    if (subtypeValue == null) {
                                        return null;
                                    }
                                    String[] names = propSubtypeInfo.subtypes().get(subtypeValue.getClass());
                                    if (names == null) {
                                        throw new IllegalStateException("Cannot find a subtype definition for class: [" + subtypeValue.getClass().getName() + "] and value [" + subtypeValue + "]");
                                    }
                                    return names[0];
                                }
                            );

                            initializers.add(ctx -> {
                                try {
                                    initProperty(serProperty, ctx);
                                } catch (SerdeException e) {
                                    throw new SerdeException("Error resolving serializer for property [" + property + "] of type [" + argument.getType().getName() + "]: " + e.getMessage(), e);
                                }
                            });

                            writeProperties.add(serProperty);
                        }

                        String originalName = argument.getName();
                        String resolvedPropertyName = resolveName(
                            propertyAnnotationMetadata,
                            originalName,
                            serdeArgumentConf,
                            propertyNamingStrategy);

                        if (argumentPropertyPredicate != null && !argumentPropertyPredicate.test(resolvedPropertyName)) {
                            continue;
                        }

                        addedProperties.add(resolvedPropertyName);

                        final SerProperty<T, Object> serProperty = new PropSerProperty<>(
                            SerBean.this,
                            resolvedPropertyName,
                            originalName,
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

                        writeProperties.add(serProperty);
                    }

                    for (BeanMethod<T, Object> jsonGetter : jsonGetters) {
                        PropertyNamingStrategy propertyNamingStrategy = getPropertyNamingStrategy(jsonGetter.getAnnotationMetadata(), encoderContext, entityPropertyNamingStrategy);
                        final AnnotationMetadata jsonGetterAnnotationMetadata = jsonGetter.getAnnotationMetadata();
                        String originalName = NameUtils.getPropertyNameForGetter(jsonGetter.getName());
                        String resolvedPropertyName = resolveName(jsonGetterAnnotationMetadata,
                            originalName,
                            serdeArgumentConf,
                            propertyNamingStrategy);

                        if (argumentPropertyPredicate != null && !argumentPropertyPredicate.test(resolvedPropertyName)) {
                            continue;
                        }

                        if (!addedProperties.add(resolvedPropertyName)) {
                            // Already added
                            continue;
                        }

                        final Argument<Object> returnType = jsonGetter.getReturnType().asArgument();
                        MethodSerProperty<T, Object> prop = new MethodSerProperty<>(SerBean.this,
                            resolvedPropertyName,
                            originalName,
                            returnType,
                            jsonGetterAnnotationMetadata,
                            jsonGetter
                        );
                        writeProperties.add(prop);
                        initializers.add(ctx -> initProperty(prop, ctx));
                    }
                } else {
                    writeProperties = new ArrayList<>();
                }

                this.wrapperProperty = introspection.stringValue(SerdeConfig.class, SerdeConfig.WRAPPER_PROPERTY).orElse(null);
            }
        }
        if (!writeProperties.isEmpty() && serdeArgumentConf != null && serdeArgumentConf.order() != null) {
            List<SerBean.SerProperty<T, Object>> orderProps = new ArrayList<>(writeProperties);
            List<SerProperty<T, Object>> order = Arrays.stream(serdeArgumentConf.order())
                    .flatMap(propName -> {
                        Optional<SerProperty<T, Object>> prop = orderProps.stream()
                            .filter(p -> p.name.equals(propName) || p.originalName.equals(propName))
                            .findFirst();
                        // Make sure we reference the property only oncemas
                        prop.ifPresent(orderProps::remove);
                        return prop.stream();
                    })
                .toList();
            writeProperties.sort(Comparator.comparingInt(order::indexOf));
        }

        this.arrayWrapperProperty = introspection.stringValue(SerdeConfig.class, SerdeConfig.ARRAY_WRAPPER_PROPERTY).orElse(null);

        simpleBean = isSimpleBean();
        boolean isAbstractIntrospection = Modifier.isAbstract(introspection.getBeanType().getModifiers());
        subtyped = isAbstractIntrospection || subtypeInfo != null && !subtypeInfo.subtypes().containsKey(type.getType()) || introspection.getAnnotationMetadata().hasDeclaredAnnotation(SerdeConfig.SerSubtyped.class);
    }

    @Nullable
    private static PropertySubtypeDescriptor findDescriptor(@Nullable SubtypeInfo subtypeInfo,
                                                            AnnotationMetadata annotationMetadata,
                                                            Argument<?> type) {
        if (subtypeInfo == null) {
            SerdeConfig.SerSubtyped.DiscriminatorType discriminatorType = annotationMetadata.enumValue(
                SerdeConfig.class,
                SerdeConfig.TYPE_DISCRIMINATOR_TYPE,
                SerdeConfig.SerSubtyped.DiscriminatorType.class
            ).orElse(null);
            if (discriminatorType == SerdeConfig.SerSubtyped.DiscriminatorType.EXISTING_PROPERTY ||
                discriminatorType == SerdeConfig.SerSubtyped.DiscriminatorType.EXTERNAL_PROPERTY) {
                return null;
            }
            String name = annotationMetadata.stringValue(SerdeConfig.class, SerdeConfig.TYPE_PROPERTY).orElse(null);
            if (name == null) {
                return null;
            }
            String value = annotationMetadata.stringValue(SerdeConfig.class, SerdeConfig.TYPE_NAME).orElse(null);
            if (value == null) {
                return null;
            }
            return new PropertySubtypeDescriptor(name, value);
        } else {
            if (subtypeInfo.discriminatorType() != SerdeConfig.SerSubtyped.DiscriminatorType.PROPERTY) {
                return null;
            }
            String[] values = subtypeInfo.subtypes().get(type.getType());
            if (values == null) {
                return null;
            }
            return new PropertySubtypeDescriptor(subtypeInfo.discriminatorName(), values[0]);
        }
    }

    public void initialize(ReentrantLock lock, Serializer.EncoderContext encoderContext) throws SerdeException {
        // Double check locking
        if (!initialized) {
            lock.lock();
            try {
                if (!initialized && !initializing) {
                    initializing = true;
                    for (Initializer initializer : initializers) {
                        initializer.initialize(encoderContext);
                    }
                    initializers = null;
                    initialized = true;
                    initializing = false;
                }
            } finally {
                lock.unlock();
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
        Argument<Z> argument = prop.argument;
        if (serdeArgumentConf != null) {
            argument = serdeArgumentConf.extendArgumentWithPrefixSuffix(argument);
        }
        if (customSer != null) {
            serializer = encoderContext.findCustomSerializer(customSer);
        } else {
            serializer = (Serializer<Z>) encoderContext.findSerializer(argument);
        }
        prop.serializer = serializer.createSpecific(encoderContext, argument);

        if (prop.serializableInto) {
            if (prop.serializer instanceof io.micronaut.serde.ObjectSerializer<Z> objectSerializer) {
                prop.objectSerializer = objectSerializer;
            } else {
                throw new SerdeException("Serializer for a property: " + prop.name + " doesn't support serializing into an existing object");
            }
        }
        prop.annotationMetadata = null;
    }

    private boolean isSimpleBean() {
        if (propertyFilter != null || jsonValue != null) {
            return false;
        }
        for (SerProperty<T, Object> property : writeProperties) {
            if (property.serializableInto || property.backRef != null || property.include != SerdeConfig.SerInclude.ALWAYS || property.views != null || property.managedRef != null) {
                return false;
            }
        }
        return true;
    }

    private PropertyNamingStrategy getPropertyNamingStrategy(AnnotationMetadata annotationMetadata,
                                                             Serializer.EncoderContext encoderContext,
                                                             PropertyNamingStrategy defaultNamingStrategy) throws SerdeException {
        Class<? extends PropertyNamingStrategy> namingStrategyClass = annotationMetadata.classValue(SerdeConfig.class, SerdeConfig.RUNTIME_NAMING)
                .orElse(null);
        return namingStrategyClass == null ? defaultNamingStrategy : encoderContext.findNamingStrategy(namingStrategyClass);
    }

    private String resolveName(AnnotationMetadata propertyAnnotationMetadata,
                               String name,
                               @Nullable
                               SerdeArgumentConf serdeArgumentConf,
                               PropertyNamingStrategy propertyNamingStrategy) {

        String resolvedName = propertyAnnotationMetadata.stringValue(SerdeConfig.class, SerdeConfig.PROPERTY).orElseGet(() -> {
            if (propertyNamingStrategy == null) {
                return null;
            }
            return propertyNamingStrategy.translate(new AnnotatedElement() {
                @Override
                public String getName() {
                    return name;
                }

                @Override
                public AnnotationMetadata getAnnotationMetadata() {
                    return propertyAnnotationMetadata;
                }
            });
        });
        if (resolvedName == null) {
            resolvedName = propertyAnnotationMetadata.stringValue(JK_PROP).orElse(name);
        }
        if (serdeArgumentConf != null) {
            return serdeArgumentConf.applyPrefixSuffix(resolvedName);
        }
        return resolvedName;
    }

    private PropertyFilter getPropertyFilterIfPresent(@Nullable BeanContext beanContext, String typeName) {
        Optional<String> filterName = introspection.stringValue(SerdeConfig.class, SerdeConfig.FILTER);
        if (beanContext != null && filterName.isPresent() && !filterName.get().isEmpty()) {
            return beanContext.findBean(PropertyFilter.class, Qualifiers.byName(filterName.get()))
                .orElseGet(() -> {
                    LoggerFactory.getLogger(SerBean.class)
                        .warn("Json filter with name '{}' was defined on type {} but no PropertyFilter bean with the name exists", filterName.get(), typeName);
                    return null;
                });
        }
        return null;
    }

    private boolean filterProperty(BeanReadProperty<T, Object> property) {
        return !property.booleanValue(SerdeConfig.class, SerdeConfig.IGNORED).orElse(false)
            && !property.booleanValue(SerdeConfig.class, SerdeConfig.IGNORED_SERIALIZATION).orElse(false)
            && !property.booleanValue(SerdeConfig.class, SerdeConfig.WRITE_ONLY).orElse(false);
    }

    static final class PropSerProperty<B, P> extends SerProperty<B, P> {

        private final UnsafeBeanReadProperty<B, P> beanProperty;

        public PropSerProperty(SerBean<B> bean, String name, String originalName, Argument<P> argument, AnnotationMetadata annotationMetadata, BeanReadProperty<B, P> beanProperty) {
            super(bean, name, originalName, argument, annotationMetadata);
            this.beanProperty = (UnsafeBeanReadProperty<B, P>) beanProperty;
        }

        @Override
        public P get(B bean) {
            return beanProperty.getUnsafe(bean);
        }
    }

    static final class MethodSerProperty<B, P> extends SerProperty<B, P> {

        private final BeanMethod<B, P> beanMethod;

        public MethodSerProperty(SerBean<B> bean, String name, String originalName, Argument<P> argument, AnnotationMetadata annotationMetadata, BeanMethod<B, P> beanMethod) {
            super(bean, name, originalName, argument, annotationMetadata);
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
            super(bean, name, name, argument);
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
            super(bean, name, name, argument);
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
        public final String originalName;
        public final Argument<P> argument;
        public final Class<?>[] views;
        public final String managedRef;
        public final String backRef;
        public final SerdeConfig.SerInclude include;
        public final boolean serializableInto;
        // Null when not initialized SerBean
        public Serializer<P> serializer;
        @Nullable
        public io.micronaut.serde.ObjectSerializer<P> objectSerializer;
        public AnnotationMetadata annotationMetadata;
        // CHECKSTYLE:ON

        public SerProperty(
                SerBean<B> bean,
                @NonNull String name,
                @NonNull String originalName,
                @NonNull Argument<P> argument) {
            this(bean, name, originalName, argument, argument.getAnnotationMetadata());
        }

        public SerProperty(
                SerBean<B> bean,
                @NonNull String name,
                @NonNull String originalName,
                @NonNull Argument<P> argument,
                @NonNull AnnotationMetadata annotationMetadata) {
            this.name = name;
            this.originalName = originalName;
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
            this.serializableInto = annotationMetadata.hasAnnotation(SerdeConfig.SerUnwrapped.class) || annotationMetadata.hasAnnotation(SerdeConfig.SerAnyGetter.class);
        }

        public abstract P get(B bean);
    }

    private interface Initializer {

        void initialize(Serializer.EncoderContext encoderContext) throws SerdeException;

    }

    private record PropertySubtypeDescriptor(String name, String value) {
    }

}
