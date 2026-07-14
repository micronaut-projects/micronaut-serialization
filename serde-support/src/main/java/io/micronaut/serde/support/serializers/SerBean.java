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
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.BeanReadProperty;
import io.micronaut.core.beans.UnsafeBeanReadProperty;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.FormatConfiguration;
import io.micronaut.serde.FormattedSerializer;
import io.micronaut.serde.Keys;
import io.micronaut.serde.PropertyFilter;
import io.micronaut.serde.SerdeIntrospections;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.SerializationConfiguration;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.exceptions.path.ReferencePath;
import io.micronaut.serde.support.util.DecoderValueKind;
import io.micronaut.serde.support.util.ObjectShapeSerdeHelper;
import io.micronaut.serde.support.util.SerdeAnnotationUtil;
import io.micronaut.serde.support.util.SerdeArgumentConf;
import io.micronaut.serde.support.util.SerdeFeatures;
import io.micronaut.serde.support.util.SubtypeInfo;
import io.micronaut.serde.util.SerdePropertyAccess;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Predicate;

@Internal
@SuppressWarnings("java:S3776")
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
    private static final String JACKSON_KEY = "com.fasterxml.jackson.annotation.JsonKey";

    // CHECKSTYLE:OFF
    public final BeanIntrospection<T> introspection;
    public final List<SerProperty<T, Object>> writeProperties;
    public final Keys propertyKeys;
    @Nullable
    public final String wrapperProperty;
    @Nullable
    public final String arrayWrapperProperty;
    @Nullable
    public final SerProperty<T, Object> typeIdProperty;
    @Nullable
    public final SerProperty<T, Object> dynamicWrapperProperty;
    @Nullable
    public final SerProperty<T, Object> dynamicArrayWrapperProperty;
    @Nullable
    public SerProperty<T, Object> jsonValue;
    @Nullable
    public SerProperty<T, Object> jsonKey;
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
                .filter(this::filterProperty)
                .toList();
        SerProperty<T, Object> resolvedTypeIdProperty = findTypeIdProperty(this, properties);
        this.typeIdProperty = resolvedTypeIdProperty;
        final Map.Entry<BeanReadProperty<T, Object>, AnnotationMetadata> serPropEntry = properties.stream()
                .filter(bp -> bp.getValue().hasAnnotation(SerdeConfig.SerValue.class) || bp.getValue().hasAnnotation(JACKSON_VALUE))
                .findFirst().orElse(null);
        jsonKey = resolveJsonKey(properties, resolvedInitializers, serdeArgumentConf);
        if (serPropEntry != null) {
            wrapperProperty = null;
            arrayWrapperProperty = null;
            dynamicWrapperProperty = null;
            dynamicArrayWrapperProperty = null;
            BeanReadProperty<T, Object> beanProperty = serPropEntry.getKey();
            final Argument<Object> serType = beanProperty.asArgument();
            AnnotationMetadata propertyAnnotationMetadata = withoutJsonKey(serPropEntry.getValue());
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
                dynamicWrapperProperty = null;
                dynamicArrayWrapperProperty = null;
                SerProperty<T, Object> resolvedJsonValue = new MethodSerProperty<>(
                    SerBean.this,
                    serMethod.getName(),
                    serMethod.getName(),
                    serMethod.getReturnType().asArgument(),
                    withoutJsonKey(serMethod.getAnnotationMetadata()),
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
                    resolvedInitializers,
                    resolvedTypeIdProperty
                );

                String arrayWrapperProperty = introspection.stringValue(SerdeConfig.class, SerdeConfig.ARRAY_WRAPPER_PROPERTY).orElse(null);
                String wrapperProperty = introspection.stringValue(SerdeConfig.class, SerdeConfig.WRAPPER_PROPERTY).orElse(null);
                SerProperty<T, Object> dynamicWrapperProperty = null;
                SerProperty<T, Object> dynamicArrayWrapperProperty = null;
                if (resolvedTypeIdProperty != null) {
                    if (wrapperProperty != null) {
                        wrapperProperty = null;
                        dynamicWrapperProperty = resolvedTypeIdProperty;
                    } else if (arrayWrapperProperty != null) {
                        arrayWrapperProperty = null;
                        dynamicArrayWrapperProperty = resolvedTypeIdProperty;
                    }
                }
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
                            if (resolvedTypeIdProperty != null) {
                                dynamicWrapperProperty = resolvedTypeIdProperty;
                            } else {
                                wrapperProperty = names[0];
                            }
                        } else if (subtypeInfo.discriminatorType() == SerdeConfig.SerSubtyped.DiscriminatorType.WRAPPER_ARRAY) {
                            if (resolvedTypeIdProperty != null) {
                                dynamicArrayWrapperProperty = resolvedTypeIdProperty;
                            } else {
                                arrayWrapperProperty = names[0];
                            }
                        }
                    } else if (resolvedTypeIdProperty != null) {
                        if (subtypeInfo.discriminatorType() == SerdeConfig.SerSubtyped.DiscriminatorType.WRAPPER_OBJECT) {
                            dynamicWrapperProperty = resolvedTypeIdProperty;
                        } else if (subtypeInfo.discriminatorType() == SerdeConfig.SerSubtyped.DiscriminatorType.WRAPPER_ARRAY) {
                            dynamicArrayWrapperProperty = resolvedTypeIdProperty;
                        }
                    }
                }

                this.wrapperProperty = wrapperProperty;
                this.arrayWrapperProperty = arrayWrapperProperty;
                this.dynamicWrapperProperty = dynamicWrapperProperty;
                this.dynamicArrayWrapperProperty = dynamicArrayWrapperProperty;
            }
        }
        sortPropertiesIfNeeded(serdeArgumentConf, introspection.getAnnotationMetadata(), serializationConfiguration, writeProperties);
        propertyKeys = Keys.create(writeProperties.stream().map(property -> property.name).toList());

        simpleBean = isSimpleBean();
        boolean isAbstractIntrospection = Modifier.isAbstract(introspection.getBeanType().getModifiers());
        subtyped = isAbstractIntrospection
            || (resolvedSubtypeInfo != null && !resolvedSubtypeInfo.subtypes().isEmpty() && !resolvedSubtypeInfo.subtypes().containsKey(type.getType()))
            || introspection.getAnnotationMetadata().hasDeclaredAnnotation(SerdeConfig.SerSubtyped.class);
    }

    @Nullable
    private SerProperty<T, Object> resolveJsonKey(Collection<Map.Entry<BeanReadProperty<T, Object>, AnnotationMetadata>> properties,
                                                  List<Initializer> resolvedInitializers,
                                                  @Nullable SerdeArgumentConf serdeArgumentConf) {
        final Map.Entry<BeanReadProperty<T, Object>, AnnotationMetadata> serKeyPropEntry = properties.stream()
            .filter(bp -> isJsonKey(bp.getValue()))
            .findFirst().orElse(null);
        if (serKeyPropEntry != null) {
            BeanReadProperty<T, Object> beanProperty = serKeyPropEntry.getKey();
            SerProperty<T, Object> resolvedJsonKey = new PropSerProperty<>(
                SerBean.this,
                beanProperty.getName(),
                beanProperty.getName(),
                beanProperty.asArgument(),
                serKeyPropEntry.getValue(),
                beanProperty
            );
            resolvedInitializers.add(ctx -> initProperty(resolvedJsonKey, ctx, serdeArgumentConf));
            return resolvedJsonKey;
        }
        final BeanMethod<T, Object> serKeyMethod = introspection.getBeanMethods().stream()
            .filter(m -> isJsonKey(m.getAnnotationMetadata()))
            .findFirst().orElse(null);
        if (serKeyMethod != null) {
            SerProperty<T, Object> resolvedJsonKey = new MethodSerProperty<>(
                SerBean.this,
                serKeyMethod.getName(),
                serKeyMethod.getName(),
                serKeyMethod.getReturnType().asArgument(),
                serKeyMethod.getAnnotationMetadata(),
                serKeyMethod
            );
            resolvedInitializers.add(ctx -> initProperty(resolvedJsonKey, ctx, serdeArgumentConf));
            return resolvedJsonKey;
        }
        return null;
    }

    private static boolean isJsonKey(AnnotationMetadata annotationMetadata) {
        return annotationMetadata.hasAnnotation(SerdeConfig.SerKey.class)
            || (annotationMetadata.hasAnnotation(JACKSON_KEY) && annotationMetadata.booleanValue(JACKSON_KEY).orElse(true));
    }

    private static AnnotationMetadata withoutJsonKey(AnnotationMetadata annotationMetadata) {
        if (!annotationMetadata.hasAnnotation(SerdeConfig.SerKey.class) && !annotationMetadata.hasAnnotation(JACKSON_KEY)) {
            return annotationMetadata;
        }
        MutableAnnotationMetadata mutableAnnotationMetadata = MutableAnnotationMetadata.of(annotationMetadata);
        mutableAnnotationMetadata.removeAnnotation(SerdeConfig.SerKey.class.getName());
        mutableAnnotationMetadata.removeAnnotation(JACKSON_KEY);
        return mutableAnnotationMetadata;
    }

    @Nullable
    private static <T> SerProperty<T, Object> findTypeIdProperty(SerBean<T> serBean,
                                                                 Collection<Map.Entry<BeanReadProperty<T, Object>, AnnotationMetadata>> properties) throws SerdeException {
        SerProperty<T, Object> typeIdProperty = null;
        for (Map.Entry<BeanReadProperty<T, Object>, AnnotationMetadata> prop : properties) {
            if (prop.getValue().hasAnnotation(SerdeConfig.SerTypeId.class)) {
                if (typeIdProperty != null) {
                    throw new SerdeException("Multiple type ids defined for type [" + serBean.introspection.getBeanType().getName() + "]");
                }
                BeanReadProperty<T, Object> property = prop.getKey();
                typeIdProperty = new PropSerProperty<>(
                    serBean,
                    property.getName(),
                    property.getName(),
                    property.asArgument(),
                    prop.getValue(),
                    property
                );
            }
        }
        return typeIdProperty;
    }

    private static <T> boolean isTypeIdPropertyUsed(@Nullable SerProperty<T, Object> typeIdProperty,
                                                    @Nullable SubtypeInfo subtypeInfo,
                                                    List<PropertySubtypeDescriptor> propertySubtypeDescriptors,
                                                    BeanIntrospection<T> introspection) {
        if (typeIdProperty == null) {
            return false;
        }
        if (!propertySubtypeDescriptors.isEmpty()) {
            return true;
        }
        if (subtypeInfo != null) {
            return subtypeInfo.discriminatorType() == SerdeConfig.SerSubtyped.DiscriminatorType.WRAPPER_OBJECT
                || subtypeInfo.discriminatorType() == SerdeConfig.SerSubtyped.DiscriminatorType.WRAPPER_ARRAY
                || subtypeInfo.discriminatorType() == SerdeConfig.SerSubtyped.DiscriminatorType.EXTERNAL_PROPERTY;
        }
        return introspection.stringValue(SerdeConfig.class, SerdeConfig.WRAPPER_PROPERTY).isPresent()
            || introspection.stringValue(SerdeConfig.class, SerdeConfig.ARRAY_WRAPPER_PROPERTY).isPresent();
    }

    @SuppressWarnings("unchecked")
    private static <B, P> UnsafeBeanReadProperty<B, P> optimizedReadProperty(BeanReadProperty<B, P> beanProperty) {
        UnsafeBeanReadProperty<B, P> unsafeBeanProperty = (UnsafeBeanReadProperty<B, P>) beanProperty;
        for (BeanProperty<B, Object> property : beanProperty.getDeclaringBean().getBeanProperties()) {
            if (!property.isWriteOnly()
                && property instanceof UnsafeBeanReadProperty
                && property.getName().equals(beanProperty.getName())
                && property.getType().equals(beanProperty.getType())) {
                return (UnsafeBeanReadProperty<B, P>) property;
            }
        }
        return unsafeBeanProperty;
    }

    private static boolean isSameProperty(BeanReadProperty<?, Object> property, SerProperty<?, Object> typeIdProperty) {
        return property.getName().equals(typeIdProperty.originalName)
            && property.getType().equals(typeIdProperty.argument.getType());
    }

    private static <T> void sortPropertiesIfNeeded(@Nullable SerdeArgumentConf serdeArgumentConf,
                                                   AnnotationMetadata annotationMetadata,
                                                   SerializationConfiguration serializationConfiguration,
                                                   List<SerProperty<T, Object>> writeProperties) {
        if (writeProperties.isEmpty()) {
            return;
        }
        String @Nullable [] explicitOrder = serdeArgumentConf == null ? null : serdeArgumentConf.order();
        boolean propertyOrder = explicitOrder != null;
        if (explicitOrder == null && annotationMetadata.isAnnotationPresent(SerdeConfig.META_ANNOTATION_PROPERTY_ORDER)) {
            explicitOrder = annotationMetadata.stringValues(SerdeConfig.META_ANNOTATION_PROPERTY_ORDER);
            if (explicitOrder.length == 0) {
                explicitOrder = null;
            }
        }
        if (explicitOrder != null) {
            String[] resolvedExplicitOrder = explicitOrder;
            @Nullable Set<String> serializedNames = propertyOrder ? null : CollectionUtils.newHashSet(writeProperties.size());
            for (SerProperty<T, Object> writeProperty : writeProperties) {
                if (serializedNames != null) {
                    serializedNames.add(writeProperty.name);
                }
            }
            writeProperties.sort(Comparator.comparingInt(property -> {
                int index = propertyOrderIndex(resolvedExplicitOrder, property, serializedNames);
                if (index >= 0) {
                    return index + 1;
                }
                return isInjectedSubtypeProperty(property) ? 0 : Integer.MAX_VALUE;
            }));
        } else if (annotationMetadata.stringValues(SerdeConfig.class, SerdeConfig.TYPE_PROPERTIES).length > 0) {
            sortJsonbTypeInfoProperties(annotationMetadata, writeProperties);
        } else if (annotationMetadata.booleanValue(SerdeConfig.META_ANNOTATION_PROPERTY_ORDER, "alphabetic").orElse(false) || serializationConfiguration.sortPropertiesAlphabetically()) {
            writeProperties.sort(Comparator.comparing(p -> p.name));
        }
    }

    private static boolean isInjectedSubtypeProperty(SerProperty<?, Object> property) {
        return property instanceof CustomSerProperty<?, ?> || property instanceof InjectedSerProperty<?, ?> || property instanceof TypeIdSerProperty<?, ?>;
    }

    private static int propertyOrderIndex(String[] explicitOrder,
                                          SerProperty<?, Object> property,
                                          @Nullable Set<String> serializedNames) {
        for (int i = 0; i < explicitOrder.length; i++) {
            String propertyName = explicitOrder[i];
            if (property.name.equals(propertyName)
                || (property.originalName.equals(propertyName) && (serializedNames == null || !serializedNames.contains(propertyName)))) {
                return i;
            }
        }
        return -1;
    }

    private static <T> void sortJsonbTypeInfoProperties(AnnotationMetadata annotationMetadata,
                                                        List<SerProperty<T, Object>> writeProperties) {
        List<String> typeProperties = List.of(annotationMetadata.stringValues(SerdeConfig.class, SerdeConfig.TYPE_PROPERTIES));
        writeProperties.sort(Comparator
            .comparingInt((SerProperty<T, Object> property) -> {
                int index = typeProperties.indexOf(property.name);
                return index < 0 ? Integer.MAX_VALUE : index;
            })
            .thenComparing(Comparator.comparingInt((SerProperty<T, Object> property) -> hierarchyDistance(property.beanType, property.getDeclaringType())).reversed())
            .thenComparing(property -> property.name));
    }

    private static int hierarchyDistance(Class<?> beanType, Class<?> declaringType) {
        int distance = 0;
        Class<?> current = beanType;
        while (current != null && current != Object.class) {
            if (current == declaringType) {
                return distance;
            }
            distance++;
            current = current.getSuperclass();
        }
        return 0;
    }

    private static <T> List<SerProperty<T, Object>> findSerializableProperties(SerBean<T> serBean,
                                                                               Argument<T> type,
                                                                               Serializer.EncoderContext encoderContext,
                                                                               @Nullable SerdeArgumentConf serdeArgumentConf,
                                                                               Collection<Map.Entry<BeanReadProperty<T, Object>, AnnotationMetadata>> properties,
                                                                               @Nullable SubtypeInfo subtypeInfo,
                                                                               SerdeIntrospections introspections,
                                                                               BeanIntrospection<T> introspection,
                                                                               List<Initializer> initializers,
                                                                               @Nullable SerProperty<T, Object> typeIdProperty) throws SerdeException {

        final List<BeanMethod<T, Object>> jsonGetters = new ArrayList<>(introspection.getBeanMethods().size());
        for (BeanMethod<T, Object> beanMethod : introspection.getBeanMethods()) {
            AnnotationMetadata annotationMetadata = beanMethod.getAnnotationMetadata();
            if ((beanMethod.isAnnotationPresent(SerdeConfig.SerGetter.class)
                || beanMethod.isAnnotationPresent(SerdeConfig.SerAnyGetter.class))
                && !annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.IGNORED).orElse(false)
                && !annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.IGNORED_SERIALIZATION).orElse(false)
                && SerdePropertyAccess.canSerialize(annotationMetadata)) {
                jsonGetters.add(beanMethod);
            }
        }

        List<PropertySubtypeDescriptor> propertySubtypeDescriptors = findDescriptors(subtypeInfo, type, introspection);
        boolean typeIdPropertyUsed = isTypeIdPropertyUsed(typeIdProperty, subtypeInfo, propertySubtypeDescriptors, introspection);

        if (properties.isEmpty() && jsonGetters.isEmpty() && propertySubtypeDescriptors.isEmpty()) {
            return List.of();
        }

        final boolean allowIgnoredProperties = introspection.booleanValue(SerdeConfig.SerIgnored.class, SerdeConfig.SerIgnored.ALLOW_SERIALIZE).orElse(false);
        @Nullable
        final Predicate<String> argumentPropertyPredicate = serdeArgumentConf == null ? null : serdeArgumentConf.resolveAllowPropertyPredicate(allowIgnoredProperties);
        final @Nullable PropertyNamingStrategy defaultPropertyNamingStrategy = encoderContext.getSerdeConfiguration().map(SerdeConfiguration::getPropertyNamingStrategy).orElse(null);
        final @Nullable PropertyNamingStrategy entityPropertyNamingStrategy = getPropertyNamingStrategy(introspection, encoderContext, defaultPropertyNamingStrategy);
        final List<SerProperty<T, Object>> writeProperties = new ArrayList<>(properties.size() + jsonGetters.size());
        for (PropertySubtypeDescriptor propertySubtypeDescriptor : propertySubtypeDescriptors) {
            if (typeIdProperty == null) {
                addSubtypeProperty(serBean, serdeArgumentConf, introspection, initializers, writeProperties, propertySubtypeDescriptor);
            } else {
                addTypeIdProperty(serBean, serdeArgumentConf, introspection, initializers, writeProperties, propertySubtypeDescriptor.propertyName(), typeIdProperty);
            }
        }
        final Set<String> addedProperties = CollectionUtils.newHashSet(properties.size());
        for (Map.Entry<BeanReadProperty<T, Object>, AnnotationMetadata> propWithAnnotations : properties) {
            final BeanReadProperty<T, Object> property = propWithAnnotations.getKey();
            if (typeIdPropertyUsed && typeIdProperty != null && isSameProperty(property, typeIdProperty)) {
                continue;
            }
            final Argument<Object> argument = property.asArgument();
            final AnnotationMetadata propertyAnnotationMetadata = propWithAnnotations.getValue();
            PropertyNamingStrategy propertyNamingStrategy = getPropertyNamingStrategy(property.getAnnotationMetadata(), encoderContext, entityPropertyNamingStrategy);

            SubtypeInfo propSubtypeInfo = SubtypeInfo.createForProperty(propertyAnnotationMetadata);
            if (propSubtypeInfo != null && propSubtypeInfo.discriminatorType() == SerdeConfig.SerSubtyped.DiscriminatorType.EXTERNAL_PROPERTY) {
                Map<Class<?>, Optional<BeanReadProperty<Object, Object>>> typeIdProperties = new ConcurrentHashMap<>();
                final CustomSerProperty<T, String> subtypeDiscriminatorProperty = new CustomSerProperty<>(
                    serBean,
                    propSubtypeInfo.discriminatorName(),
                    Argument.STRING,
                    bean -> {
                        Object subtypeValue = property.get(bean);
                        if (subtypeValue == null) {
                            return null;
                        }
                        String typeId = findExternalTypeId(introspections, typeIdProperties, subtypeValue);
                        if (typeId != null) {
                            return typeId;
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
    private static String findExternalTypeId(SerdeIntrospections introspections,
                                             Map<Class<?>, Optional<BeanReadProperty<Object, Object>>> typeIdProperties,
                                             Object subtypeValue) {
        Optional<BeanReadProperty<Object, Object>> typeIdProperty = typeIdProperties.computeIfAbsent(
            subtypeValue.getClass(),
            subtypeClass -> findExternalTypeIdProperty(introspections, subtypeClass)
        );
        if (typeIdProperty.isEmpty()) {
            return null;
        }
        Object typeIdValue = typeIdProperty.get().get(subtypeValue);
        if (typeIdValue == null) {
            throw new IllegalStateException("Property [" + typeIdProperty.get().getName() + "] of type ["
                + subtypeValue.getClass().getName() + "] returned null");
        }
        return typeIdValue.toString();
    }

    @SuppressWarnings("unchecked")
    private static Optional<BeanReadProperty<Object, Object>> findExternalTypeIdProperty(SerdeIntrospections introspections, Class<?> subtypeClass) {
        try {
            BeanIntrospection<Object> subtypeIntrospection = introspections.getSerializableIntrospection(
                Argument.of((Class<Object>) subtypeClass)
            );
            BeanReadProperty<Object, Object> typeIdProperty = null;
            for (BeanReadProperty<Object, Object> property : subtypeIntrospection.getBeanReadProperties()) {
                if (property.getAnnotationMetadata().hasAnnotation(SerdeConfig.SerTypeId.class)) {
                    if (typeIdProperty != null) {
                        throw new IllegalStateException("Multiple type ids defined for type [" + subtypeClass.getName() + "]");
                    }
                    typeIdProperty = property;
                }
            }
            return Optional.ofNullable(typeIdProperty);
        } catch (IntrospectionException e) {
            return Optional.empty();
        }
    }

    private static <T> void addTypeIdProperty(SerBean<T> serBean,
                                              @Nullable SerdeArgumentConf serdeArgumentConf,
                                              BeanIntrospection<T> introspection,
                                              List<Initializer> initializers,
                                              List<SerProperty<T, Object>> writeProperties,
                                              String propertyName,
                                              SerProperty<T, Object> typeIdProperty) {
        SerProperty<T, Object> prop = new TypeIdSerProperty<>(
            serBean,
            propertyName,
            typeIdProperty
        );
        writeProperties.add(prop);
        initializers.add(context -> {
            try {
                initProperty(prop, context, serdeArgumentConf);
            } catch (SerdeException e) {
                throw new IntrospectionException("Error configuring subtype binding for type " + introspection.getBeanType() + ": " + e.getMessage());
            }
        });
    }

    private static <T> void addSubtypeProperty(SerBean<T> serBean,
                                               @Nullable SerdeArgumentConf serdeArgumentConf,
                                               BeanIntrospection<T> introspection,
                                               List<Initializer> initializers,
                                               List<SerProperty<T, Object>> writeProperties,
                                               PropertySubtypeDescriptor propertySubtypeDescriptor) {
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

    private static List<PropertySubtypeDescriptor> findDescriptors(@Nullable SubtypeInfo subtypeInfo,
                                                                   Argument<?> argument,
                                                                   BeanIntrospection<?> beanIntrospection) {
        if (subtypeInfo == null) {
            List<PropertySubtypeDescriptor> typeProperties = findTypeProperties(argument.getAnnotationMetadata());
            if (typeProperties.isEmpty()) {
                return findTypeProperties(beanIntrospection.getAnnotationMetadata());
            }
            return typeProperties;
        }
        if (subtypeInfo.discriminatorType() != SerdeConfig.SerSubtyped.DiscriminatorType.PROPERTY) {
            return List.of();
        }
        String[] names = subtypeInfo.subtypes().get(beanIntrospection.getBeanType());
        if (names == null) {
            names = beanIntrospection.stringValues(SerdeConfig.class, SerdeConfig.TYPE_NAMES);
        }
        if (names == null || names.length == 0) {
            return List.of();
        }
        return List.of(new PropertySubtypeDescriptor(subtypeInfo.discriminatorName(), names[0]));
    }

    private static List<PropertySubtypeDescriptor> findTypeProperties(AnnotationMetadata annotationMetadata) {
        String[] names = annotationMetadata.stringValues(SerdeConfig.class, SerdeConfig.TYPE_PROPERTIES);
        String[] values = annotationMetadata.stringValues(SerdeConfig.class, SerdeConfig.TYPE_PROPERTY_VALUES);
        if (names.length > 0 || values.length > 0) {
            int length = Math.min(names.length, values.length);
            List<PropertySubtypeDescriptor> descriptors = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                descriptors.add(new PropertySubtypeDescriptor(names[i], values[i]));
            }
            return descriptors;
        }
        String name = annotationMetadata.stringValue(SerdeConfig.class, SerdeConfig.TYPE_PROPERTY).orElse(null);
        if (name == null) {
            return List.of();
        }
        String value = annotationMetadata.stringValue(SerdeConfig.class, SerdeConfig.TYPE_NAME).orElse(null);
        if (value == null) {
            return List.of();
        }
        return List.of(new PropertySubtypeDescriptor(name, value));
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
        if (propertyFilter != null || jsonValue != null || dynamicWrapperProperty != null || dynamicArrayWrapperProperty != null) {
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

    private boolean filterProperty(Map.Entry<BeanReadProperty<T, Object>, AnnotationMetadata> property) {
        AnnotationMetadata annotationMetadata = property.getValue();
        return !annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.IGNORED).orElse(false)
            && !annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.IGNORED_SERIALIZATION).orElse(false)
            && SerdePropertyAccess.canSerialize(property.getKey(), annotationMetadata);
    }

    static final class PropSerProperty<B, P> extends SerProperty<B, P> {

        private final UnsafeBeanReadProperty<B, P> beanProperty;

        public PropSerProperty(SerBean<B> bean, String name, String originalName, Argument<P> argument, AnnotationMetadata annotationMetadata, BeanReadProperty<B, P> beanProperty) {
            super(bean, name, originalName, argument, annotationMetadata);
            this.beanProperty = optimizedReadProperty(beanProperty);
        }

        @Override
        public @Nullable P get(B bean) {
            return beanProperty.getUnsafe(bean);
        }

        @Override
        public boolean serializeDirectPrimitive(Encoder encoder, B bean, byte valueKind) throws IOException {
            if (!primitive) {
                return false;
            }
            switch (valueKind) {
                case DecoderValueKind.BOOLEAN_CODE -> encoder.encodeBoolean(beanProperty.getBooleanUnsafe(bean));
                case DecoderValueKind.BYTE_CODE -> encoder.encodeByte(beanProperty.getByteUnsafe(bean));
                case DecoderValueKind.SHORT_CODE -> encoder.encodeShort(beanProperty.getShortUnsafe(bean));
                case DecoderValueKind.CHAR_CODE -> encoder.encodeChar(beanProperty.getCharUnsafe(bean));
                case DecoderValueKind.INT_CODE -> encoder.encodeInt(beanProperty.getIntUnsafe(bean));
                case DecoderValueKind.LONG_CODE -> encoder.encodeLong(beanProperty.getLongUnsafe(bean));
                case DecoderValueKind.FLOAT_CODE -> encoder.encodeFloat(beanProperty.getFloatUnsafe(bean));
                case DecoderValueKind.DOUBLE_CODE -> encoder.encodeDouble(beanProperty.getDoubleUnsafe(bean));
                default -> {
                    return false;
                }
            }
            return true;
        }

        @Override
        public Class<?> getDeclaringType() {
            return beanProperty.getDeclaringType();
        }
    }

    static final class TypeIdSerProperty<B, P> extends SerProperty<B, P> {

        private final SerProperty<B, P> typeIdProperty;

        public TypeIdSerProperty(SerBean<B> bean, String name, SerProperty<B, P> typeIdProperty) {
            super(bean, name, typeIdProperty.originalName, typeIdProperty.argument);
            this.typeIdProperty = typeIdProperty;
        }

        @Override
        public @Nullable P get(B bean) {
            return typeIdProperty.get(bean);
        }

        @Override
        public Class<?> getDeclaringType() {
            return typeIdProperty.getDeclaringType();
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
        public final boolean primitive;
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
            this.primitive = argument.isPrimitive();
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

        public Class<?> getDeclaringType() {
            return beanType;
        }

        public abstract @Nullable P get(B bean);

        public boolean serializeDirectPrimitive(Encoder encoder, B bean, byte valueKind) throws IOException {
            return false;
        }

        String getRequiredString(B bean) throws SerdeException {
            P value = get(bean);
            if (value == null) {
                throw new SerdeException("Property [" + originalName + "] of type [" + getDeclaringType().getName() + "] returned null");
            }
            return value.toString();
        }
    }

    private interface Initializer {

        void initialize(Serializer.EncoderContext encoderContext) throws SerdeException;

    }

    private record PropertySubtypeDescriptor(String propertyName, String subtypeName) {
    }

}
