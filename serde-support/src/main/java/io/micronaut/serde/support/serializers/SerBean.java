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
import io.micronaut.serde.FormatConfiguration;
import io.micronaut.serde.FormattedSerializer;
import io.micronaut.serde.PropertyFilter;
import io.micronaut.serde.SerdeIntrospections;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.SerializationConfiguration;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.exceptions.path.ReferencePath;
import io.micronaut.serde.support.util.ObjectShapeSerdeHelper;
import io.micronaut.serde.support.util.SerdeAnnotationUtil;
import io.micronaut.serde.support.util.SerdeArgumentConf;
import io.micronaut.serde.support.util.SerdeFeatures;
import io.micronaut.serde.support.util.SubtypeInfo;
import org.jspecify.annotations.Nullable;
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
import java.util.Objects;
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
    @Nullable
    public final PropertyFilter propertyFilter;
    @Nullable
    public final SubtypeInfo subtypeInfo;

    private volatile boolean initialized;
    private volatile boolean initializing;

    @Nullable
    private List<Initializer> initializers = new ArrayList<>();

    // CHECKSTYLE:ON

    SerBean(Argument<T> type,
            SerdeIntrospections introspections,
            Serializer.EncoderContext encoderContext,
            @Nullable SerdeArgumentConf serdeArgumentConf,
            SerializationConfiguration serializationConfiguration,
            @Nullable BeanContext beanContext) throws SerdeException {
        // !!! Avoid accessing annotations from the argument, the annotations are not included in the cache key
        this.introspection = introspections.getSerializableIntrospection(type);
        encoderContext = SerdeFeatures.withFeatures(encoderContext, introspection.getAnnotationMetadata());
        this.configuration = encoderContext.getSerializationConfiguration().orElse(serializationConfiguration);
        this.propertyFilter = getPropertyFilterIfPresent(beanContext, type.getSimpleName());
        @Nullable SubtypeInfo resolvedSubtypeInfo = serdeArgumentConf == null ? null : serdeArgumentConf.getSubtypeInfo();
        this.subtypeInfo = resolvedSubtypeInfo;
        List<Initializer> resolvedInitializers = Objects.requireNonNull(initializers);

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
            arrayWrapperProperty = null;
            BeanReadProperty<T, Object> beanProperty = serPropEntry.getKey();
            final Argument<Object> serType = beanProperty.asArgument();
            AnnotationMetadata propertyAnnotationMetadata = serPropEntry.getValue();
            SerProperty<T, Object> resolvedJsonValue = new PropSerProperty<>(
                SerBean.this,
                beanProperty.getName(),
                beanProperty.getName(),
                serType,
                propertyAnnotationMetadata,
                beanProperty
            );
            jsonValue = resolvedJsonValue;
            resolvedInitializers.add(ctx -> initProperty(resolvedJsonValue, ctx, serdeArgumentConf));
            writeProperties = Collections.emptyList();
        } else {

            final BeanMethod<T, Object> serMethod = introspection.getBeanMethods().stream()
                    .filter(m -> m.isAnnotationPresent(SerdeConfig.SerValue.class) || m.getAnnotationMetadata().hasAnnotation(JACKSON_VALUE))
                    .findFirst().orElse(null);
            if (serMethod != null) {
                wrapperProperty = null;
                arrayWrapperProperty = null;
                SerProperty<T, Object> resolvedJsonValue = new MethodSerProperty<>(
                    SerBean.this,
                    serMethod.getName(),
                    serMethod.getName(),
                    serMethod.getReturnType().asArgument(),
                    serMethod.getAnnotationMetadata(),
                    serMethod
                );
                jsonValue = resolvedJsonValue;
                resolvedInitializers.add(ctx -> initProperty(resolvedJsonValue, ctx, serdeArgumentConf));
                writeProperties = Collections.emptyList();
            } else {
                writeProperties = findSerializableProperties(
                    this,
                    type,
                    encoderContext,
                    serdeArgumentConf,
                    properties,
                    resolvedSubtypeInfo,
                    introspections,
                    introspection,
                    resolvedInitializers
                );

                String arrayWrapperProperty = introspection.stringValue(SerdeConfig.class, SerdeConfig.ARRAY_WRAPPER_PROPERTY).orElse(null);
                String wrapperProperty = introspection.stringValue(SerdeConfig.class, SerdeConfig.WRAPPER_PROPERTY).orElse(null);
                if (subtypeInfo != null) {
                    String[] names = introspection.getAnnotationMetadata().stringValues(SerdeConfig.class, SerdeConfig.TYPE_NAMES);
                    if (names.length == 0) {
                        SubtypeInfo typeSubtypeInfo = SubtypeInfo.createForType(introspection.getAnnotationMetadata());
                        if (typeSubtypeInfo != null) {
                            names = typeSubtypeInfo.subtypes().get(introspection.getBeanType());
                        }
                    }
                    if (names != null && names.length > 0) {
                        if (subtypeInfo.discriminatorType() == SerdeConfig.SerSubtyped.DiscriminatorType.WRAPPER_OBJECT) {
                            wrapperProperty = names[0];
                        } else if (subtypeInfo.discriminatorType() == SerdeConfig.SerSubtyped.DiscriminatorType.WRAPPER_ARRAY) {
                            arrayWrapperProperty = names[0];
                        }
                    }
                }

                this.wrapperProperty = wrapperProperty;
                this.arrayWrapperProperty = arrayWrapperProperty;
            }
        }
        sortPropertiesIfNeeded(serdeArgumentConf, introspection.getAnnotationMetadata(), serializationConfiguration, writeProperties);

        simpleBean = isSimpleBean();
        boolean isAbstractIntrospection = Modifier.isAbstract(introspection.getBeanType().getModifiers());
        subtyped = isAbstractIntrospection || resolvedSubtypeInfo != null && !resolvedSubtypeInfo.subtypes().isEmpty() && !resolvedSubtypeInfo.subtypes().containsKey(type.getType()) || introspection.getAnnotationMetadata().hasDeclaredAnnotation(SerdeConfig.SerSubtyped.class);
    }

    private static <T> void sortPropertiesIfNeeded(@Nullable SerdeArgumentConf serdeArgumentConf,
                                                   AnnotationMetadata annotationMetadata,
                                                   SerializationConfiguration serializationConfiguration,
                                                   List<SerProperty<T, Object>> writeProperties) {
        if (writeProperties.isEmpty()) {
            return;
        }
        String @Nullable [] explicitOrder = serdeArgumentConf == null ? null : serdeArgumentConf.order();
        if (explicitOrder == null && annotationMetadata.isAnnotationPresent(SerdeConfig.META_ANNOTATION_PROPERTY_ORDER)) {
            explicitOrder = annotationMetadata.stringValues(SerdeConfig.META_ANNOTATION_PROPERTY_ORDER);
            if (explicitOrder.length == 0) {
                explicitOrder = null;
            }
        }
        if (explicitOrder != null) {
            List<SerProperty<T, Object>> orderProps = new ArrayList<>(writeProperties);
            List<SerProperty<T, Object>> order = Arrays.stream(explicitOrder)
                .flatMap(propName -> {
                    Optional<SerProperty<T, Object>> prop = orderProps.stream()
                        .filter(p -> p.name.equals(propName) || p.originalName.equals(propName))
                        .findFirst();
                    // Make sure we reference the property only once
                    prop.ifPresent(orderProps::remove);
                    return prop.stream();
                })
                .toList();
            writeProperties.sort(Comparator.comparingInt(property -> {
                int index = order.indexOf(property);
                return index < 0 ? Integer.MAX_VALUE : index;
            }));
        } else if (annotationMetadata.booleanValue(SerdeConfig.META_ANNOTATION_PROPERTY_ORDER, "alphabetic").orElse(false) || serializationConfiguration.sortPropertiesAlphabetically()) {
            writeProperties.sort(Comparator.comparing(p -> p.name));
        }
    }

    private static <T> List<SerProperty<T, Object>> findSerializableProperties(SerBean<T> serBean,
                                                                               Argument<T> type,
                                                                               Serializer.EncoderContext encoderContext,
                                                                               @Nullable SerdeArgumentConf serdeArgumentConf,
                                                                               Collection<Map.Entry<BeanReadProperty<T, Object>, AnnotationMetadata>> properties,
                                                                               @Nullable SubtypeInfo subtypeInfo,
                                                                               SerdeIntrospections introspections,
                                                                               BeanIntrospection<T> introspection,
                                                                               List<Initializer> initializers) throws SerdeException {

        final List<BeanMethod<T, Object>> jsonGetters = new ArrayList<>(introspection.getBeanMethods().size());
        for (BeanMethod<T, Object> beanMethod : introspection.getBeanMethods()) {
            if (beanMethod.isAnnotationPresent(SerdeConfig.SerGetter.class)
                || beanMethod.isAnnotationPresent(SerdeConfig.SerAnyGetter.class)) {
                jsonGetters.add(beanMethod);
            }
        }

        PropertySubtypeDescriptor propertySubtypeDescriptor = findDescriptor(subtypeInfo, type, introspection);

        if (properties.isEmpty() && jsonGetters.isEmpty() && propertySubtypeDescriptor == null) {
            return List.of();
        }

        final boolean allowIgnoredProperties = introspection.booleanValue(SerdeConfig.SerIgnored.class, SerdeConfig.SerIgnored.ALLOW_SERIALIZE).orElse(false);
        @Nullable
        final Predicate<String> argumentPropertyPredicate = serdeArgumentConf == null ? null : serdeArgumentConf.resolveAllowPropertyPredicate(allowIgnoredProperties);
        final @Nullable PropertyNamingStrategy defaultPropertyNamingStrategy = encoderContext.getSerdeConfiguration().map(SerdeConfiguration::getPropertyNamingStrategy).orElse(null);
        final @Nullable PropertyNamingStrategy entityPropertyNamingStrategy = getPropertyNamingStrategy(introspection, encoderContext, defaultPropertyNamingStrategy);
        final List<SerProperty<T, Object>> writeProperties = new ArrayList<>(properties.size() + jsonGetters.size());
        if (propertySubtypeDescriptor != null) {
            SerProperty<T, String> prop;
            String propertyName = propertySubtypeDescriptor.propertyName;
            if (SerdeConfig.TYPE_NAME_CLASS_SIMPLE_NAME_PLACEHOLDER.equals(propertySubtypeDescriptor.subtypeName)) {
                prop = new CustomSerProperty<>(serBean,
                    propertyName,
                    Argument.of(String.class, propertyName),
                    t -> t.getClass().getSimpleName());
            } else {
                prop = new InjectedSerProperty<>(serBean,
                    propertyName,
                    Argument.of(String.class, propertyName),
                    propertySubtypeDescriptor.subtypeName);
            }
            writeProperties.add((SerProperty) prop);
            initializers.add(context -> {
                try {
                    initProperty(prop, context, serdeArgumentConf);
                } catch (SerdeException e) {
                    throw new IntrospectionException("Error configuring subtype binding for type " + introspection.getBeanType() + ": " + e.getMessage());
                }
            });
        }
        final Set<String> addedProperties = CollectionUtils.newHashSet(properties.size());
        for (Map.Entry<BeanReadProperty<T, Object>, AnnotationMetadata> propWithAnnotations : properties) {
            final BeanReadProperty<T, Object> property = propWithAnnotations.getKey();
            final Argument<Object> argument = property.asArgument();
            final AnnotationMetadata propertyAnnotationMetadata = propWithAnnotations.getValue();
            PropertyNamingStrategy propertyNamingStrategy = getPropertyNamingStrategy(property.getAnnotationMetadata(), encoderContext, entityPropertyNamingStrategy);

            SubtypeInfo propSubtypeInfo = SubtypeInfo.createForProperty(propertyAnnotationMetadata);
            if (propSubtypeInfo != null && propSubtypeInfo.discriminatorType() == SerdeConfig.SerSubtyped.DiscriminatorType.EXTERNAL_PROPERTY) {
                final CustomSerProperty<T, String> subtypeDiscriminatorProperty = new CustomSerProperty<>(
                    serBean,
                    propSubtypeInfo.discriminatorName(),
                    Argument.STRING,
                    bean -> {
                        Object subtypeValue = property.get(bean);
                        if (subtypeValue == null) {
                            return null;
                        }
                        String[] names = propSubtypeInfo.subtypes().get(subtypeValue.getClass());
                        if (names == null) {
                            try {
                                names = introspections.getSerializableIntrospection(Argument.of(subtypeValue.getClass()))
                                    .stringValues(SerdeConfig.class, SerdeConfig.TYPE_NAMES);
                            } catch (Exception ignore) {
                                // Not introspected
                            }
                        }
                        if (names == null || names.length == 0) {
                            throw new IllegalStateException("Cannot find a subtype definition for class: [" + subtypeValue.getClass().getName() + "] and value [" + subtypeValue + "]");
                        }
                        return names[0];
                    }
                );

                initializers.add(ctx -> {
                    try {
                        initProperty(subtypeDiscriminatorProperty, ctx, serdeArgumentConf);
                    } catch (SerdeException e) {
                        throw new SerdeException("Error resolving serializer for property [" + property + "] of type [" + argument.getType().getName() + "]: " + e.getMessage(), e);
                    }
                });
                writeProperties.add((SerProperty) subtypeDiscriminatorProperty);
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
                serBean,
                resolvedPropertyName,
                originalName,
                argument,
                propertyAnnotationMetadata,
                property
            );

            initializers.add(ctx -> {
                try {
                    initProperty(serProperty, ctx, serdeArgumentConf);
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

            MethodSerProperty<T, Object> prop = new MethodSerProperty<>(serBean,
                resolvedPropertyName,
                originalName,
                jsonGetter.getReturnType().asArgument(),
                jsonGetterAnnotationMetadata,
                jsonGetter
            );
            writeProperties.add(prop);
            initializers.add(ctx -> initProperty(prop, ctx, serdeArgumentConf));
        }
        return writeProperties;
    }

    @Nullable
    private static PropertySubtypeDescriptor findDescriptor(@Nullable SubtypeInfo subtypeInfo,
                                                            Argument<?> argument,
                                                            BeanIntrospection<?> beanIntrospection) {
        if (subtypeInfo == null) {
            PropertySubtypeDescriptor typeProperty = findTypeProperty(argument.getAnnotationMetadata());
            if (typeProperty == null) {
                return findTypeProperty(beanIntrospection.getAnnotationMetadata());
            }
            return typeProperty;
        }
        if (subtypeInfo.discriminatorType() != SerdeConfig.SerSubtyped.DiscriminatorType.PROPERTY) {
            return null;
        }
        String[] names = subtypeInfo.subtypes().get(beanIntrospection.getBeanType());
        if (names == null) {
            names = beanIntrospection.stringValues(SerdeConfig.class, SerdeConfig.TYPE_NAMES);
        }
        if (names == null || names.length == 0) {
            return null;
        }
        return new PropertySubtypeDescriptor(subtypeInfo.discriminatorName(), names[0]);
    }

    @Nullable
    private static PropertySubtypeDescriptor findTypeProperty(AnnotationMetadata annotationMetadata) {
        String name = annotationMetadata.stringValue(SerdeConfig.class, SerdeConfig.TYPE_PROPERTY).orElse(null);
        if (name == null) {
            return null;
        }
        String value = annotationMetadata.stringValue(SerdeConfig.class, SerdeConfig.TYPE_NAME).orElse(null);
        if (value == null) {
            return null;
        }
        return new PropertySubtypeDescriptor(name, value);
    }

    public void initialize(ReentrantLock lock, Serializer.EncoderContext encoderContext) throws SerdeException {
        encoderContext = SerdeFeatures.withFeatures(encoderContext, introspection.getAnnotationMetadata());
        // Double check locking
        if (!initialized) {
            lock.lock();
            try {
                if (!initialized && !initializing) {
                    initializing = true;
            for (Initializer initializer : Objects.requireNonNull(initializers)) {
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

    private static <Y, Z> void initProperty(SerProperty<Y, Z> prop, Serializer.EncoderContext encoderContext, @Nullable SerdeArgumentConf serdeArgumentConf) throws SerdeException {
        if (prop.serializer != null) {
            return;
        }

        AnnotationMetadata annotationMetadata = Objects.requireNonNull(prop.annotationMetadata);
        Class customSer = annotationMetadata.classValue(SerdeConfig.class, SerdeConfig.SERIALIZER_CLASS).orElse(null);
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
        Serializer.EncoderContext propertyContext = encoderContext.withFeatures(prop.featuresWith, prop.featuresWithout);
        prop.serializer = prop.format == null
            ? serializer.createSpecific(propertyContext, argument)
            : createSpecific(prop.format, serializer, propertyContext, argument);

        if (prop.serializableInto) {
            if (prop.serializer instanceof io.micronaut.serde.ObjectSerializer<Z> objectSerializer) {
                prop.objectSerializer = objectSerializer;
            } else {
                throw new SerdeException("Serializer for a property: " + prop.name + " doesn't support serializing into an existing object");
            }
        }
        prop.annotationMetadata = null;
    }

    private static <T> Serializer<T> createSpecific(FormatConfiguration configuration,
                                                    Serializer<T> serializer,
                                                    Serializer.EncoderContext encoderContext,
                                                    Argument<T> argument) throws SerdeException {
        if (serializer instanceof FormattedSerializer<T> formattedSerializer) {
            return formattedSerializer.createSpecific(encoderContext, argument, configuration);
        }
        Serializer<T> specific = serializer.createSpecific(encoderContext, argument);
        FormatConfiguration.Shape shape = configuration.shape();
        if (shape == FormatConfiguration.Shape.ANY) {
            return specific;
        }
        if (shape.isPojoShape()) {
            return ObjectShapeSerdeHelper.objectSerializer(encoderContext, argument);
        }
        return specific;
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

    @Nullable
    private static PropertyNamingStrategy getPropertyNamingStrategy(AnnotationMetadata annotationMetadata,
                                                             Serializer.EncoderContext encoderContext,
                                                             @Nullable PropertyNamingStrategy defaultNamingStrategy) throws SerdeException {
        Class<? extends PropertyNamingStrategy> namingStrategyClass = annotationMetadata.classValue(SerdeConfig.class, SerdeConfig.RUNTIME_NAMING)
                .orElse(null);
        return namingStrategyClass == null ? defaultNamingStrategy : encoderContext.findNamingStrategy(namingStrategyClass);
    }

    private static String resolveName(AnnotationMetadata propertyAnnotationMetadata,
                               String name,
                               @Nullable
                               SerdeArgumentConf serdeArgumentConf,
                               @Nullable PropertyNamingStrategy propertyNamingStrategy) {

        String resolvedName = propertyAnnotationMetadata.stringValue(SerdeConfig.class, SerdeConfig.PROPERTY).orElse(null);
        if (resolvedName == null && propertyNamingStrategy != null) {
            resolvedName = propertyNamingStrategy.translate(new AnnotatedElement() {
                @Override
                public String getName() {
                    return name;
                }

                @Override
                public AnnotationMetadata getAnnotationMetadata() {
                    return propertyAnnotationMetadata;
                }
            });
        }
        if (resolvedName == null) {
            resolvedName = propertyAnnotationMetadata.stringValue(JK_PROP).orElse(name);
        }
        if (serdeArgumentConf != null) {
            return serdeArgumentConf.applyPrefixSuffix(resolvedName);
        }
        return resolvedName;
    }

    @Nullable
    private PropertyFilter getPropertyFilterIfPresent(@Nullable BeanContext beanContext, String typeName) {
        Optional<String> filterName = introspection.stringValue(SerdeConfig.class, SerdeConfig.FILTER);
        if (beanContext != null && filterName.isPresent() && !filterName.get().isEmpty()) {
            Optional<PropertyFilter> propertyFilter = beanContext.findBean(PropertyFilter.class, Qualifiers.byName(filterName.get()));
            if (propertyFilter.isPresent()) {
                return propertyFilter.get();
            }
            LoggerFactory.getLogger(SerBean.class)
                .warn("Json filter with name '{}' was defined on type {} but no PropertyFilter bean with the name exists", filterName.get(), typeName);
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
        public @Nullable P get(B bean) {
            return beanProperty.getUnsafe(bean);
        }
    }

    static final class MethodSerProperty<B, P> extends SerProperty<B, P> {

        private final BeanMethod<B, P> beanMethod;

        public MethodSerProperty(SerBean<B> bean, String name, String originalName, Argument<P> argument, AnnotationMetadata annotationMetadata, BeanMethod<B, P> beanMethod) {
            super(bean, name, originalName, argument.withName(name), annotationMetadata);
            this.beanMethod = beanMethod;
        }

        @Override
        public @Nullable P get(B bean) {
            return beanMethod.invoke(bean);
        }
    }

    static final class CustomSerProperty<B, P> extends SerProperty<B, P> {

        private final Function<B, @Nullable P> reader;

        public CustomSerProperty(SerBean<B> bean, String name, Argument<P> argument, Function<B, @Nullable P> reader) {
            super(bean, name, name, argument);
            this.reader = reader;
        }

        @Override
        public @Nullable P get(B bean) {
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
        public @Nullable P get(B bean) {
            return injected;
        }
    }

    @Internal
    abstract static class SerProperty<B, P> {
        // CHECKSTYLE:OFF
        public final Class<?> beanType;
        public final String name;
        public final String originalName;
        public final Argument<P> argument;
        public final Class<?> @Nullable [] views;
        @Nullable
        public final String managedRef;
        @Nullable
        public final String backRef;
        public final SerdeConfig.SerInclude include;
        public final boolean serializableInto;
        // Null when not initialized SerBean
        @Nullable
        public Serializer<P> serializer;
        public io.micronaut.serde.@Nullable ObjectSerializer<P> objectSerializer;
        @Nullable
        public AnnotationMetadata annotationMetadata;
        @Nullable
        public final FormatConfiguration format;
        public final Set<SerializationConfiguration.Feature> featuresWith;
        public final Set<SerializationConfiguration.Feature> featuresWithout;
        // CHECKSTYLE:ON

        public SerProperty(
                SerBean<B> bean,
                String name,
                String originalName,
                Argument<P> argument) {
            this(bean, name, originalName, argument, argument.getAnnotationMetadata());
        }

        public SerProperty(
                SerBean<B> bean,
                String name,
                String originalName,
                Argument<P> argument,
                AnnotationMetadata annotationMetadata) {
            this.beanType = bean.introspection.getBeanType();
            this.name = name;
            this.originalName = originalName;
            this.argument = annotationMetadata.isEmpty() ? argument : argument.withAnnotationMetadata(annotationMetadata);
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
            FormatConfiguration propertyFormat = FormatConfiguration.from(annotationMetadata);
            if (propertyFormat == null) {
                FormatConfiguration beanFormat = FormatConfiguration.from(beanMetadata);
                if (beanFormat != null && switch (beanFormat.shape()) {
                    case ARRAY, OBJECT, POJO -> false;
                    default -> true;
                }) {
                    propertyFormat = beanFormat;
                }
            }
            this.format = propertyFormat;
            this.featuresWith = SerdeFeatures.serializationFeaturesWith(annotationMetadata);
            this.featuresWithout = SerdeFeatures.serializationFeaturesWithout(annotationMetadata);
            this.serializableInto = annotationMetadata.hasAnnotation(SerdeConfig.SerUnwrapped.class) || annotationMetadata.hasAnnotation(SerdeConfig.SerAnyGetter.class);
        }

        public ReferencePath getReferencePath() {
            return ReferencePath.ofProperty(beanType, argument);
        }

        public abstract @Nullable P get(B bean);
    }

    private interface Initializer {

        void initialize(Serializer.EncoderContext encoderContext) throws SerdeException;

    }

    private record PropertySubtypeDescriptor(String propertyName, String subtypeName) {
    }

}
