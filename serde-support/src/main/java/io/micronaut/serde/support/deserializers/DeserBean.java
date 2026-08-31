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
package io.micronaut.serde.support.deserializers;

import io.micronaut.core.annotation.AnnotatedElement;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanMethod;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.BeanReadProperty;
import io.micronaut.core.beans.BeanWriteProperty;
import io.micronaut.core.beans.UnsafeBeanWriteProperty;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.GenericPlaceholder;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.FormatConfiguration;
import io.micronaut.serde.FormattedDeserializer;
import io.micronaut.serde.KeyDescriptor;
import io.micronaut.serde.Keys;
import io.micronaut.serde.UpdatingDeserializer;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;
import io.micronaut.serde.exceptions.InvalidFormatException;
import io.micronaut.serde.exceptions.InvalidPropertyFormatException;
import io.micronaut.serde.exceptions.NullValueSerdeException;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.exceptions.path.ReferencePath;
import io.micronaut.serde.support.util.DecoderValueKind;
import io.micronaut.serde.support.util.DocumentIdUtil;
import io.micronaut.serde.support.util.ObjectShapeSerdeHelper;
import io.micronaut.serde.support.util.SerdeAnnotationUtil;
import io.micronaut.serde.support.util.SerdeArgumentConf;
import io.micronaut.serde.support.util.SerdeFeatures;
import io.micronaut.serde.support.util.SubtypeInfo;
import io.micronaut.serde.util.GeneratedSerdeExceptionUtil;
import io.micronaut.serde.util.SerdePropertyAccess;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Holder for data about a deserializable bean.
 *
 * @param <T> The generic type
 */
@Internal
final class DeserBean<T> {
    private static final String JK_PROP = "com.fasterxml.jackson.annotation.JsonProperty";

    // CHECKSTYLE:OFF
    public final BeanIntrospection<T> introspection;
    @Nullable
    public final PropertiesBag<T> creatorParams;
    public final DerProperty<T, Object> @Nullable [] creatorUnwrapped;
    @Nullable
    public final PropertiesBag<T> injectProperties;
    public final DerProperty<T, Object> @Nullable [] unwrappedProperties;
    @Nullable
    public final AnySetter anySetter;
    @Nullable
    public final String wrapperProperty;
    @Nullable
    public final DeserBeanSubtypeInfo<T> subtypeInfo;
    @Nullable
    public final Set<String> ignoredProperties;
    @Nullable
    public final Set<String> externalProperties;
    public final boolean isJsonValueProperty;

    public final int creatorSize;
    public final int injectPropertiesSize;

    public final boolean ignoreUnknown;
    public final boolean failOnNullForPrimitives;
    public final boolean delegating;
    public final boolean simpleBean;
    public final boolean recordLikeBean;
    public final boolean acceptCaseInsensitiveProperties;

    public final boolean hasBuilder;
    public final ConversionService conversionService;
    public final Keys propertyKeys;

    private final Map<String, Argument<?>> typeArguments;
    private final List<String> propertyKeyNames;
    @Nullable
    private final IgnoredPropertyKeys ignoredPropertyKeys;
    /**
     * The document-scoped identifier property (JAXB {@code @XmlID} or Jackson {@code @JsonIdentityInfo}), if any.
     */
    @Nullable
    public final BeanProperty<T, Object> documentIdProperty;
    /**
     * Whether {@link #documentIdProperty} carries object identity semantics.
     */
    public final boolean objectIdentity;

    private volatile boolean initialized;
    private volatile boolean initializing;

    // CHECKSTYLE:ON

    public DeserBean(DeserializationConfiguration defaultDeserializationConfiguration,
                     Argument<T> type,
                     BeanIntrospection<T> introspection,
                     Deserializer.DecoderContext decoderContext,
                     DeserBeanRegistry deserBeanRegistry,
                     @Nullable SerdeArgumentConf serdeArgumentConf) throws SerdeException {
        this(defaultDeserializationConfiguration, type.getTypeVariables(), introspection, decoderContext, deserBeanRegistry, serdeArgumentConf);
    }

    public DeserBean(DeserializationConfiguration defaultDeserializationConfiguration,
                     Map<String, Argument<?>> typeArguments,
                     BeanIntrospection<T> introspection,
                     Deserializer.DecoderContext decoderContext,
                     DeserBeanRegistry deserBeanRegistry,
                     @Nullable SerdeArgumentConf serdeArgumentConf)
        throws SerdeException {

        this.typeArguments = typeArguments;
        decoderContext = SerdeFeatures.withFeatures(decoderContext, introspection.getAnnotationMetadata());

        @Nullable PropertyNamingStrategy defaultPropertyNamingStrategy = decoderContext.getSerdeConfiguration().map(SerdeConfiguration::getPropertyNamingStrategy).orElse(null);
        this.conversionService = decoderContext.getConversionService();
        this.introspection = introspection;
        this.documentIdProperty = DocumentIdUtil.findDocumentIdProperty(introspection);
        this.objectIdentity = DocumentIdUtil.hasObjectIdentity(documentIdProperty);
        final SerdeConfig.SerCreatorMode creatorMode = introspection
            .getConstructor().getAnnotationMetadata()
            .enumValue(Creator.class, "mode", SerdeConfig.SerCreatorMode.class)
            .orElse(null);
        delegating = creatorMode == SerdeConfig.SerCreatorMode.DELEGATING;
        hasBuilder = introspection.hasBuilder() &&
            // always deserialize with record constructor
            !introspection.getBeanType().isRecord();
        final Argument<?>[] constructorArguments = hasBuilder ? introspection.builder().getBuildMethodArguments() : introspection.getConstructorArguments();
        creatorSize = constructorArguments.length;
        @Nullable PropertyNamingStrategy entityPropertyNamingStrategy = getPropertyNamingStrategy(introspection, decoderContext, defaultPropertyNamingStrategy);

        Set<String> ignoredProperties = new HashSet<>();
        Set<String> externalProperties = new HashSet<>();

        @Nullable
        Predicate<String> allowPropertyPredicate = serdeArgumentConf == null ? null : serdeArgumentConf.resolveAllowPropertyPredicate(false);

        // Replicating Jackson behaviour: @JsonIncludeProperties will ignore any not-included properties
        boolean hasIncludedProperties = (serdeArgumentConf != null && serdeArgumentConf.getIncluded() != null)
            || introspection.isAnnotationPresent(SerdeConfig.SerIncluded.class);
        DeserializationConfiguration deserializationConfiguration = decoderContext.getDeserializationConfiguration().orElse(defaultDeserializationConfiguration);
        this.ignoreUnknown = hasIncludedProperties || introspection.booleanValue(SerdeConfig.SerIgnored.class, SerdeConfig.SerIgnored.IGNORE_UNKNOWN)
            .orElse(deserializationConfiguration.isIgnoreUnknown());
        this.failOnNullForPrimitives = deserializationConfiguration.isFailOnNullForPrimitives();
        this.acceptCaseInsensitiveProperties = acceptCaseInsensitiveProperties(decoderContext);

        List<String> propertyKeyNames = new ArrayList<>();
        final PropertiesBag.Builder<T> creatorPropertiesBuilder = new PropertiesBag.Builder<>(introspection, constructorArguments.length, acceptCaseInsensitiveProperties);
        final PropertiesBag.Builder<T> unwrappedPropertiesBuilder = new PropertiesBag.Builder<>(introspection, constructorArguments.length, acceptCaseInsensitiveProperties);

        BeanMethod<T, Object> jsonValueMethod = null;
        BeanProperty<T, Object> jsonValueProperty = introspection.getBeanProperties()
            .stream()
            .filter(m -> m.isAnnotationPresent(SerdeConfig.SerValue.class))
            .findFirst()
            .orElse(null);

        if (jsonValueProperty != null) {
            if (constructorArguments.length != 1) {
                throw new SerdeException("Cannot have multiple parameters for a json value constructor!");
            }
        }

        List<DerProperty<T, ?>> creatorUnwrapped = null;
        AnySetter anySetterValue = null;
        List<DerProperty<T, ?>> unwrappedProperties = null;
        for (int i = 0; i < constructorArguments.length; i++) {
            Argument<Object> constructorArgument = resolveArgument((Argument<Object>) constructorArguments[i]);
            String unresolvedTypeVariableName = null;
            if (constructorArgument instanceof GenericPlaceholder<?> genericPlaceholder) {
                unresolvedTypeVariableName = genericPlaceholder.getVariableName();
            }
            final AnnotationMetadata annotationMetadata = resolveArgumentMetadata(introspection, constructorArgument, constructorArgument.getAnnotationMetadata());
            if (annotationMetadata.isAnnotationPresent(SerdeConfig.SerAnySetter.class)) {
                anySetterValue = new AnySetter(constructorArgument, i);
                continue;
            }

            SubtypeInfo propertySubtypeInfo = SubtypeInfo.createForProperty(annotationMetadata);
            if (propertySubtypeInfo != null && propertySubtypeInfo.discriminatorType() == SerdeConfig.SerSubtyped.DiscriminatorType.EXTERNAL_PROPERTY) {
                externalProperties.add(propertySubtypeInfo.discriminatorName());
            }

            PropertyNamingStrategy propertyNamingStrategy = getPropertyNamingStrategy(annotationMetadata, decoderContext, entityPropertyNamingStrategy);
            final String propertyName = resolveName(serdeArgumentConf, constructorArgument, annotationMetadata, propertyNamingStrategy);

            boolean isIgnored = isIgnored(annotationMetadata) || (allowPropertyPredicate != null && !allowPropertyPredicate.test(propertyName));
            if (isIgnored) {
                ignoredProperties.add(propertyName);
            }

            Argument<Object> constructorWithPropertyArgument = constructorArgument.withAnnotationMetadata(annotationMetadata);
            final boolean isUnwrapped = annotationMetadata.hasAnnotation(SerdeConfig.SerUnwrapped.class);
            DeserBean<Object> unwrapped = null;
            if (isUnwrapped && !constructorArgument.getType().equals(Object.class)) {
                unwrapped = deserBeanRegistry.getDeserializableBean(
                    serdeArgumentConf == null ? constructorWithPropertyArgument : serdeArgumentConf.extendArgumentWithPrefixSuffix(constructorWithPropertyArgument),
                    typeArguments,
                    decoderContext
                );
            }
            DerProperty<T, Object> derProperty = new DerProperty<>(
                conversionService,
                introspection,
                i,
                propertyName,
                constructorWithPropertyArgument,
                isIgnored ? null : introspection.getProperty(propertyName)
                    .or(() -> introspection.getProperty(constructorArgument.getName()))
                    .orElse(null),
                null,
                unwrapped,
                null,
                isIgnored,
                deserializationConfiguration,
                failOnNullForPrimitives,
                unresolvedTypeVariableName
            );
            if (isUnwrapped) {
                if (creatorUnwrapped == null) {
                    creatorUnwrapped = new ArrayList<>();
                }
                creatorUnwrapped.add(derProperty);
                unwrappedPropertiesBuilder.register(propertyName, derProperty, false);
            } else {
                creatorPropertiesBuilder.register(propertyName, derProperty, true);
            }
        }

        this.creatorParams = creatorUnwrapped == null ? creatorPropertiesBuilder.build(propertyKeyNames) : creatorPropertiesBuilder.buildNotNull(propertyKeyNames);
        final PropertiesBag<T> unwrappedParams = unwrappedPropertiesBuilder.build(new ArrayList<>());

        if (hasBuilder) {
            PropertiesBag.Builder<T> readPropertiesBuilder = new PropertiesBag.Builder<>(introspection, introspection.getBeanProperties().size(), acceptCaseInsensitiveProperties);
            BeanIntrospection.Builder<T> builder = introspection.builder();
            Argument<?>[] builderArguments = builder.getBuilderArguments();

            for (int i = 0; i < builderArguments.length; i++) {
                Argument<Object> builderArgument = (Argument<Object>) builderArguments[i];
                AnnotationMetadata annotationMetadata = builderArgument.getAnnotationMetadata();
                Optional<BeanProperty<T, Object>> matchingOuterProperty = introspection.getProperty(builderArgument.getName());
                PropertyNamingStrategy propertyNamingStrategy = getPropertyNamingStrategy(annotationMetadata, decoderContext, entityPropertyNamingStrategy);
                final String jsonProperty = resolveName(
                    serdeArgumentConf,
                    builderArgument,
                    matchingOuterProperty
                        .map(outer -> List.of(annotationMetadata, outer.getAnnotationMetadata()))
                        .orElse(List.of(annotationMetadata)),
                    propertyNamingStrategy
                );
                final DerProperty<T, Object> derProperty = new DerProperty<>(
                    conversionService,
                    introspection,
                    i,
                    jsonProperty,
                    builderArgument,
                    null,
                    null,
                    null,
                    null,
                    false,
                    deserializationConfiguration,
                    failOnNullForPrimitives,
                    null
                );
                readPropertiesBuilder.register(jsonProperty, derProperty, true);
            }
            injectProperties = readPropertiesBuilder.build(propertyKeyNames);
        } else {
            final Collection<BeanMethod<T, Object>> beanMethods = introspection.getBeanMethods();
            final List<BeanMethod<T, Object>> jsonSetters = new ArrayList<>(beanMethods.size());
            BeanMethod<T, Object> anySetter = null;
            for (BeanMethod<T, Object> method : beanMethods) {
                if (method.isAnnotationPresent(SerdeConfig.SerSetter.class)) {
                    jsonSetters.add(method);
                } else if (method.isAnnotationPresent(SerdeConfig.SerAnySetter.class) && ArrayUtils.isNotEmpty(method.getArguments())) {
                    anySetter = method;
                } else if (method.isAnnotationPresent(SerdeConfig.SerValue.class) && ArrayUtils.isEmpty(method.getArguments())) {
                    jsonValueMethod = method;
                }
            }

            if (anySetterValue == null) {
                anySetterValue = (anySetter != null ? new AnySetter((BeanMethod<Object, ?>) anySetter) : null);
            }

            Collection<BeanWriteProperty<T, Object>> beanProperties = introspection.getBeanWriteProperties();
            if (!beanProperties.isEmpty() || !jsonSetters.isEmpty()) {
                PropertiesBag.Builder<T> readPropertiesBuilder = new PropertiesBag.Builder<>(introspection, introspection.getBeanProperties().size(), acceptCaseInsensitiveProperties);
                int i = -1;
                for (BeanWriteProperty<T, Object> beanProperty : beanProperties) {
                    final AnnotationMetadata annotationMetadata = beanProperty.getAnnotationMetadata();
                    final String propertyName = resolveName(
                        serdeArgumentConf,
                        beanProperty,
                        annotationMetadata,
                        getPropertyNamingStrategy(annotationMetadata, decoderContext, entityPropertyNamingStrategy)
                    );
                    SubtypeInfo propertySubtypeInfo = SubtypeInfo.createForProperty(annotationMetadata);
                    if (propertySubtypeInfo != null && propertySubtypeInfo.discriminatorType() == SerdeConfig.SerSubtyped.DiscriminatorType.EXTERNAL_PROPERTY) {
                        externalProperties.add(propertySubtypeInfo.discriminatorName());
                    }
                    if ((creatorParams != null && creatorParams.contains(propertyName))
                        || (unwrappedParams != null && unwrappedParams.contains(propertyName))) {
                        continue;
                    }
                    if (isIgnored(beanProperty, annotationMetadata) || (allowPropertyPredicate != null && !allowPropertyPredicate.test(propertyName))) {
                        ignoredProperties.add(propertyName);
                        continue;
                    }
                    i++;

                    // Remove any ignored conflicting properties
                    ignoredProperties.remove(propertyName);

                    if (annotationMetadata.isAnnotationPresent(SerdeConfig.SerAnySetter.class)) {
                        anySetterValue = new AnySetter((BeanWriteProperty<Object, Object>) beanProperty);
                    } else {
                        final boolean isUnwrapped = annotationMetadata.hasAnnotation(SerdeConfig.SerUnwrapped.class);
                        final Argument<Object> propertyArgument = resolveArgument(beanProperty.asArgument());
                        String unresolvedTypeVariableName = null;
                        if (propertyArgument instanceof GenericPlaceholder<?> genericPlaceholder) {
                            unresolvedTypeVariableName = genericPlaceholder.getVariableName();
                        }

                        DeserBean<Object> unwrapped = null;
                        if (isUnwrapped) {
                            unwrapped = deserBeanRegistry.getDeserializableBean(
                                serdeArgumentConf == null ? propertyArgument : serdeArgumentConf.extendArgumentWithPrefixSuffix(propertyArgument),
                                typeArguments,
                                decoderContext
                            );
                        }

                        final DerProperty<T, Object> derProperty = new DerProperty<>(
                            conversionService,
                            introspection,
                            i,
                            propertyName,
                            propertyArgument,
                            beanProperty,
                            null,
                            unwrapped,
                            null,
                            false,
                            deserializationConfiguration,
                            failOnNullForPrimitives,
                            unresolvedTypeVariableName
                        );
                        if (isUnwrapped) {
                            if (unwrappedProperties == null) {
                                unwrappedProperties = new ArrayList<>();
                            }
                            unwrappedProperties.add(derProperty);
                        }
                        readPropertiesBuilder.register(propertyName, derProperty, true);
                    }
                }

                for (BeanMethod<T, Object> jsonSetter : jsonSetters) {
                    i++;
                    PropertyNamingStrategy propertyNamingStrategy = getPropertyNamingStrategy(jsonSetter.getAnnotationMetadata(), decoderContext, entityPropertyNamingStrategy);
                    final String property = resolveName(serdeArgumentConf,
                        new AnnotatedElement() {
                            @Override
                            public String getName() {
                                return NameUtils.getPropertyNameForSetter(jsonSetter.getName());
                            }

                            @Override
                            public AnnotationMetadata getAnnotationMetadata() {
                                return jsonSetter.getAnnotationMetadata();
                            }
                        },
                        jsonSetter.getAnnotationMetadata(),
                        propertyNamingStrategy
                    );
                    final Argument<Object> argument = resolveArgument((Argument<Object>) jsonSetter.getArguments()[0]);
                    String unresolvedTypeVariableName = null;
                    if (argument instanceof GenericPlaceholder<?> genericPlaceholder) {
                        unresolvedTypeVariableName = genericPlaceholder.getVariableName();
                    }
                    final DerProperty<T, Object> derProperty = new DerProperty<>(
                        conversionService,
                        introspection,
                        i,
                        property,
                        argument,
                        null,
                        jsonSetter,
                        null,
                        null,
                        false,
                        deserializationConfiguration,
                        failOnNullForPrimitives,
                        unresolvedTypeVariableName
                    );
                    readPropertiesBuilder.register(property, derProperty, true);
                }
                injectProperties = readPropertiesBuilder.build(propertyKeyNames);
            } else {
                injectProperties = null;
            }
        }
        this.injectPropertiesSize = injectProperties == null ? 0 : injectProperties.getDerProperties().size();
        this.wrapperProperty = introspection.stringValue(SerdeConfig.class, SerdeConfig.WRAPPER_PROPERTY).orElse(null);

        this.anySetter = anySetterValue;

        //noinspection unchecked
        this.creatorUnwrapped = creatorUnwrapped != null ? creatorUnwrapped.toArray(new DerProperty[0]) : null;
        //noinspection unchecked
        this.unwrappedProperties = unwrappedProperties != null ? unwrappedProperties.toArray(new DerProperty[0]) : null;

        SubtypeInfo subtypeInfoBase = serdeArgumentConf == null ? SubtypeInfo.createForType(introspection) : serdeArgumentConf.getSubtypeInfo();
        subtypeInfo = DeserBeanSubtypeInfo.create(subtypeInfoBase == null ? SubtypeInfo.createForType(introspection) : subtypeInfoBase,
            introspection, decoderContext, this, deserializationConfiguration, deserBeanRegistry);

        String discriminatorProperty = introspection.stringValue(SerdeConfig.class, SerdeConfig.TYPE_PROPERTY).orElse(null);
        if (discriminatorProperty != null && !introspection.booleanValue(SerdeConfig.class, SerdeConfig.TYPE_PROPERTY_VISIBLE).orElse(false)) {
            ignoredProperties.add(discriminatorProperty);
        }
        boolean allowIgnoredProperties = introspection.booleanValue(SerdeConfig.SerIgnored.class, SerdeConfig.SerIgnored.ALLOW_DESERIALIZE).orElse(false);
        if (!allowIgnoredProperties && serdeArgumentConf != null && serdeArgumentConf.getIgnored() != null) {
            ignoredProperties.addAll(
                Arrays.asList(
                    serdeArgumentConf.getIgnored()
                )
            );
        }
        if (ignoredProperties.isEmpty()) {
            this.ignoredProperties = null;
        } else {
            this.ignoredProperties = ignoredProperties;
        }
        if (externalProperties.isEmpty()) {
            this.externalProperties = null;
        } else {
            this.externalProperties = externalProperties;
        }
        this.ignoredPropertyKeys = IgnoredPropertyKeys.create(this.ignoredProperties, propertyKeyNames, acceptCaseInsensitiveProperties);
        addSubtypeKeys(propertyKeyNames, subtypeInfo, acceptCaseInsensitiveProperties);
        addUnwrappedKeys(propertyKeyNames, this.creatorUnwrapped, acceptCaseInsensitiveProperties);
        addUnwrappedKeys(propertyKeyNames, this.unwrappedProperties, acceptCaseInsensitiveProperties);
        this.propertyKeyNames = List.copyOf(propertyKeyNames);
        this.propertyKeys = createPropertyKeys(this.propertyKeyNames, acceptCaseInsensitiveProperties);

        isJsonValueProperty = jsonValueMethod != null || jsonValueProperty != null;

        simpleBean = isSimpleBean();
        recordLikeBean = isRecordLikeBean();
    }

    /**
     * Registers the document-scoped identifier of a fully read instance so that identifier references to it can be resolved.
     */
    void registerDocumentId(Deserializer.DecoderContext decoderContext, T instance) {
        BeanProperty<T, Object> property = documentIdProperty;
        if (property == null) {
            return;
        }
        Object id = property.get(instance);
        if (id != null) {
            DocumentIdUtil.register(decoderContext, id, introspection, property.asArgument(), instance);
        }
    }

    private Keys createPropertyKeys(List<String> keyNames, boolean caseInsensitive) {
        @Nullable List<KeyDescriptor> descriptors = null;
        for (int i = 0; i < keyNames.size(); i++) {
            DerProperty<T, Object> property = propertyForKeyIndex(i);
            if (descriptors != null) {
                descriptors.add(property == null || !hasKeyMetadata(property)
                    ? new KeyDescriptor(keyNames.get(i))
                    : keyDescriptor(keyNames.get(i), property));
            } else if (property != null && hasKeyMetadata(property)) {
                descriptors = new ArrayList<>(keyNames.size());
                for (int j = 0; j < i; j++) {
                    descriptors.add(new KeyDescriptor(keyNames.get(j)));
                }
                descriptors.add(keyDescriptor(keyNames.get(i), property));
            }
        }
        return descriptors == null
            ? Keys.create(keyNames, caseInsensitive)
            : Keys.createWithMetadata(descriptors, caseInsensitive);
    }

    private @Nullable DerProperty<T, Object> propertyForKeyIndex(int keyIndex) {
        DerProperty<T, Object> property = injectProperties == null ? null : injectProperties.property(keyIndex);
        if (property == null && creatorParams != null) {
            property = creatorParams.property(keyIndex);
        }
        return property;
    }

    private static boolean hasKeyMetadata(DerProperty<?, ?> property) {
        return property.xmlAttributeProperty
            || property.xmlTextProperty
            || property.xmlCDataProperty
            || property.xmlListProperty
            || property.xmlMixedProperty
            || property.xmlNamespace != null
            || property.xmlWrappingConfigured
            || property.xmlWrapperName != null
            || property.xmlWrapperNamespace != null
            || property.xmlDefaultValue != null
            || property.xmlNillable != null
            || property.xmlWrapperNillable != null;
    }

    private static KeyDescriptor keyDescriptor(String name, DerProperty<?, ?> property) {
        Map<String, String> metadata = CollectionUtils.newHashMap(10);
        if (property.xmlAttributeProperty) {
            metadata.put(SerdeConfig.XML_ATTRIBUTE_PROPERTY, "true");
        }
        if (property.xmlTextProperty) {
            metadata.put(SerdeConfig.XML_TEXT_PROPERTY, "true");
        }
        if (property.xmlCDataProperty) {
            metadata.put(SerdeConfig.XML_CDATA_PROPERTY, "true");
        }
        if (property.xmlListProperty) {
            metadata.put(SerdeConfig.XML_LIST_PROPERTY, "true");
        }
        if (property.xmlMixedProperty) {
            metadata.put(SerdeConfig.XML_MIXED_PROPERTY, "true");
        }
        if (property.xmlNamespace != null) {
            metadata.put(SerdeConfig.XML_NAMESPACE, property.xmlNamespace);
        }
        if (property.xmlWrappingConfigured) {
            metadata.put(SerdeConfig.META_ANNOTATION_PROPERTY, Boolean.toString(property.xmlUseWrapping));
        }
        if (property.xmlWrapperName != null) {
            metadata.put(SerdeConfig.WRAPPER_PROPERTY, property.xmlWrapperName);
        }
        if (property.xmlWrapperNamespace != null) {
            metadata.put(SerdeConfig.XML_WRAPPER_NAMESPACE, property.xmlWrapperNamespace);
        }
        if (property.xmlDefaultValue != null) {
            metadata.put(SerdeConfig.XML_DEFAULT_VALUE, property.xmlDefaultValue);
        }
        if (property.xmlNillable != null) {
            metadata.put(SerdeConfig.XML_NILLABLE, property.xmlNillable.toString());
        }
        if (property.xmlWrapperNillable != null) {
            metadata.put(SerdeConfig.XML_WRAPPER_NILLABLE, property.xmlWrapperNillable.toString());
        }
        return new KeyDescriptor(name, metadata);
    }

    String propertyKeyName(int keyIndex) {
        return propertyKeyNames.get(keyIndex);
    }

    int propertyKeyCount() {
        return propertyKeyNames.size();
    }

    List<String> propertyKeyNames() {
        return propertyKeyNames;
    }

    int propertyKeyIndexOf(String propertyName) {
        return propertyKeys.indexOf(propertyName);
    }

    boolean isIgnoredPropertyKey(int keyIndex) {
        return ignoredPropertyKeys != null && ignoredPropertyKeys.contains(keyIndex);
    }

    boolean isKnownPropertyKey(int keyIndex) {
        return (creatorParams != null && creatorParams.containsKeyIndex(keyIndex))
            || (injectProperties != null && injectProperties.containsKeyIndex(keyIndex));
    }

    boolean isKnownProperty(String propertyName) {
        return (creatorParams != null && creatorParams.contains(propertyName))
            || (injectProperties != null && injectProperties.contains(propertyName));
    }

    void initialize(ReentrantLock lock, Deserializer.DecoderContext decoderContext) throws SerdeException {
        decoderContext = SerdeFeatures.withFeatures(decoderContext, introspection.getAnnotationMetadata());
        // Double check locking
        if (!initialized) {
            lock.lock();
            try {
                if (!initialized && !initializing) {
                    initializing = true;
                    try {
                        initializeInternal(decoderContext);
                        initialized = true;
                    } finally {
                        initializing = false;
                    }
                }
            } finally {
                lock.unlock();
            }
        }
    }

    private void initializeInternal(Deserializer.DecoderContext decoderContext) throws SerdeException {
        if (injectProperties != null) {
            for (DerProperty<T, Object> property : injectProperties.getProperties()) {
                initProperty(property, decoderContext);
            }
        }
        if (creatorParams != null) {
            for (DerProperty<T, Object> property : creatorParams.getProperties()) {
                initProperty(property, decoderContext);
            }
        }
        if (anySetter != null) {
            anySetter.deserializer = anySetter.valueType.equalsType(Argument.OBJECT_ARGUMENT) ? null : findDeserializer(decoderContext, anySetter.valueType);
        }
        if (unwrappedProperties != null) {
            for (DerProperty<T, Object> unwrappedProperty : unwrappedProperties) {
                initProperty(unwrappedProperty, decoderContext);
            }
        }
    }

    private boolean isSimpleBean() {
        if (documentIdProperty != null || isJsonValueProperty || ignoredProperties != null || externalProperties != null || delegating || subtypeInfo != null || creatorParams != null || creatorUnwrapped != null || unwrappedProperties != null || anySetter != null) {
            return false;
        }
        if (injectProperties != null) {
            if (!injectProperties.hasIdentityKeyIndexes() || injectProperties.propertiesMask() == 0) {
                return false;
            }
            for (DerProperty<T, Object> property : injectProperties.getProperties()) {
                if (property.unresolvedTypeVariableName != null || property.isAnySetter || property.views != null || property.aliases != null || property.managedRef != null || introspection != property.introspection || property.backRef != null || property.beanProperty == null || property.merge || property.idReference == SerdeConfig.IdReference.OBJECT_OR_ID) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isRecordLikeBean() {
        if (documentIdProperty != null || isJsonValueProperty || ignoredProperties != null || externalProperties != null || delegating || subtypeInfo != null || injectProperties != null || creatorUnwrapped != null || unwrappedProperties != null || anySetter != null) {
            return false;
        }
        if (creatorParams != null) {
            if (!creatorParams.hasIdentityKeyIndexes() || creatorParams.propertiesMask() == 0) {
                return false;
            }
            for (DerProperty<T, Object> property : creatorParams.getProperties()) {
                if (property.unresolvedTypeVariableName != null || property.isAnySetter || property.views != null || property.aliases != null || property.managedRef != null || introspection != property.introspection || property.backRef != null) {
                    return false;
                }
            }
        }
        return true;
    }

    private void initProperty(DerProperty<T, Object> property, Deserializer.DecoderContext decoderContext) throws SerdeException {
        if (property.ignored) {
            return;
        }
        Deserializer.DecoderContext propertyContext = decoderContext.withFeatures(property.featuresWith, property.featuresWithout);
        property.deserializer = unwrapErrorCatching(property.format == null
            ? findDeserializerWithoutFormat(propertyContext, property.argument)
            : findDeserializer(propertyContext, property.argument, property.format));
        if (property.format == null && !property.hasFeatureOverrides && property.deserializer instanceof DecoderValueKind.Provider decoderValueKind) {
            property.decoderValueKind = decoderValueKind.decoderValueKind().code();
        }
        if (property.merge
            && property.beanReadProperty != null
            && property.format == null
            && !property.hasFeatureOverrides
            && !property.argument.getType().isArray()
            && !(property.deserializer instanceof UpdatingDeserializer)) {
            UpdatingDeserializer<Object> mergeDeserializer = ObjectShapeSerdeHelper.updatingObjectDeserializer(propertyContext, property.argument);
            if (mergeDeserializer != null) {
                property.mergeDeserializer = unwrapErrorCatching(mergeDeserializer);
            }
        }
    }

    private static <T> Deserializer<T> unwrapErrorCatching(Deserializer<T> deserializer) {
        if (deserializer instanceof ErrorCatchingDeserializer<T> errorCatchingDeserializer) {
            return errorCatchingDeserializer.getDeserializer();
        }
        return deserializer;
    }

    @Nullable
    private PropertyNamingStrategy getPropertyNamingStrategy(AnnotationMetadata annotationMetadata,
                                                             Deserializer.DecoderContext decoderContext,
                                                             @Nullable PropertyNamingStrategy defaultNamingStrategy) throws SerdeException {
        Class<? extends PropertyNamingStrategy> namingStrategyClass = annotationMetadata.classValue(SerdeConfig.class, SerdeConfig.RUNTIME_NAMING)
            .orElse(null);
        return namingStrategyClass == null ? defaultNamingStrategy : decoderContext.findNamingStrategy(namingStrategyClass);
    }

    private <A> Argument<A> resolveArgument(Argument<A> argument) {
        if (argument instanceof GenericPlaceholder || argument.hasTypeVariables()) {
            if (!typeArguments.isEmpty()) {
                return resolveArgument(argument, typeArguments);
            }
        }
        return argument;
    }

    @SuppressWarnings("unchecked")
    private <A> Argument<A> resolveArgument(Argument<A> argument, Map<String, Argument<?>> bounds) {
        Argument<?>[] declaredParameters = argument.getTypeParameters();
        if (argument instanceof GenericPlaceholder<A> gp) {
            Argument<?> resolved = bounds.get(gp.getVariableName());
            if (resolved != null) {
                return (Argument<A>) Argument.of(
                    resolved.getType(),
                    argument.getName(),
                    argument.getAnnotationMetadata(),
                    resolveParameters(bounds, resolved.getTypeParameters())
                );
            }
            Argument<?>[] typeParameters = resolveParameters(bounds, declaredParameters);
            if (typeParameters != declaredParameters) {
                return Argument.ofTypeVariable(
                    argument.getType(),
                    argument.getName(),
                    gp.getVariableName(),
                    gp.getAnnotationMetadata(),
                    typeParameters
                );
            }
        } else {
            Argument<?>[] typeParameters = resolveParameters(bounds, declaredParameters);
            if (typeParameters != declaredParameters) {
                return Argument.of(
                    argument.getType(),
                    argument.getName(),
                    argument.getAnnotationMetadata(),
                    typeParameters
                );
            }
        }
        return argument;
    }

    private Argument<?>[] resolveParameters(Map<String, Argument<?>> bounds, Argument<?>[] typeParameters) {
        if (ArrayUtils.isEmpty(typeParameters)) {
            return typeParameters;
        }
        Argument<?>[] resolvedParameters = new Argument[typeParameters.length];
        boolean differ = false;
        for (int i = 0; i < typeParameters.length; i++) {
            Argument<?> typeParameter = typeParameters[i];
            Argument<?> resolved = resolveArgument(typeParameter, bounds);
            if (resolved != typeParameter) {
                resolvedParameters[i] = resolved;
                differ = true;
            } else {
                resolvedParameters[i] = typeParameter;
            }
        }
        return differ ? resolvedParameters : typeParameters;
    }

    private String resolveName(@Nullable SerdeArgumentConf serdeArgumentConf,
                               AnnotatedElement annotatedElement,
                               AnnotationMetadata annotationMetadata,
                               @Nullable PropertyNamingStrategy namingStrategy) {
        return resolveName(serdeArgumentConf, annotatedElement, List.of(annotationMetadata), namingStrategy);
    }

    private String resolveName(@Nullable SerdeArgumentConf serdeArgumentConf,
                               AnnotatedElement annotatedElement,
                               List<AnnotationMetadata> annotationMetadata,
                               @Nullable PropertyNamingStrategy namingStrategy) {
        String name = resolveName(annotatedElement, annotationMetadata, namingStrategy);
        if (serdeArgumentConf != null) {
            return serdeArgumentConf.applyPrefixSuffix(name);
        }
        return name;
    }

    private String resolveName(AnnotatedElement annotatedElement,
                               List<AnnotationMetadata> annotationMetadata,
                               @Nullable PropertyNamingStrategy namingStrategy) {
        for (AnnotationMetadata metadataElement : annotationMetadata) {
            Optional<String> serde = metadataElement.stringValue(SerdeConfig.class, SerdeConfig.PROPERTY);
            if (serde.isPresent()) {
                return serde.get();
            }
            Optional<String> jackson = metadataElement.stringValue(JK_PROP);
            if (jackson.isPresent()) {
                return jackson.get();
            }
        }
        if (namingStrategy != null) {
            return namingStrategy.translate(annotatedElement);
        }
        return annotatedElement.getName();
    }

    private static boolean acceptCaseInsensitiveProperties(Deserializer.DecoderContext decoderContext) {
        return decoderContext.getFeatures().contains(DeserializationConfiguration.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES);
    }

    private static <T> Deserializer<T> findDeserializer(Deserializer.DecoderContext decoderContext, Argument<T> argument) throws SerdeException {
        decoderContext = SerdeFeatures.withFeatures(decoderContext, argument.getAnnotationMetadata());
        FormatConfiguration configuration = FormatConfiguration.from(argument.getAnnotationMetadata());
        return configuration == null
            ? findDeserializerWithoutFormat(decoderContext, argument)
            : findDeserializer(decoderContext, argument, configuration);
    }

    private static <T> Deserializer<T> findDeserializer(Deserializer.DecoderContext decoderContext,
                                                        Argument<T> argument,
                                                        FormatConfiguration configuration) throws SerdeException {
        SpecificDeserializer<T> specificDeserializer = resolveDeserializer(decoderContext, argument);
        return createSpecific(configuration, specificDeserializer.deserializer(), decoderContext, specificDeserializer.argument());
    }

    private static <T> Deserializer<T> findDeserializerWithoutFormat(Deserializer.DecoderContext decoderContext,
                                                                     Argument<T> argument) throws SerdeException {
        SpecificDeserializer<T> specificDeserializer = resolveDeserializer(decoderContext, argument);
        return specificDeserializer.deserializer().createSpecific(decoderContext, specificDeserializer.argument());
    }

    private static <T> Deserializer<T> createSpecific(FormatConfiguration configuration,
                                                      Deserializer<T> deserializer,
                                                      Deserializer.DecoderContext decoderContext,
                                                      Argument<T> argument) throws SerdeException {
        if (deserializer instanceof FormattedDeserializer<T> formattedDeserializer) {
            return formattedDeserializer.createSpecific(decoderContext, argument, configuration);
        }
        Deserializer<T> specific = deserializer.createSpecific(decoderContext, argument);
        FormatConfiguration.Shape shape = configuration.shape();
        if (shape == FormatConfiguration.Shape.ANY) {
            return specific;
        }
        if (shape.isPojoShape()) {
            return ObjectShapeSerdeHelper.objectDeserializer(decoderContext, argument);
        }
        return specific;
    }

    private static <T> SpecificDeserializer<T> resolveDeserializer(Deserializer.DecoderContext decoderContext,
                                                                   Argument<T> argument) throws SerdeException {
        Class customDeser = argument.getAnnotationMetadata().classValue(SerdeConfig.class, SerdeConfig.DESERIALIZER_CLASS).orElse(null);
        Deserializer<T> deserializer;
        if (customDeser != null) {
            deserializer = (Deserializer<T>) decoderContext.findCustomDeserializer(customDeser);
        } else {
            Class<T> deserializeAs = argument.getAnnotationMetadata().classValue(SerdeConfig.class, SerdeConfig.DESERIALIZE_AS).orElse(null);
            if (deserializeAs != null) {
                argument = Argument.of(
                    deserializeAs,
                    argument.getName(),
                    argument.getAnnotationMetadata(),
                    argument.getTypeParameters()
                );
            }
            Argument<T> lookupArgument = normalizeLookupArgument(argument);
            deserializer = (Deserializer<T>) decoderContext.findDeserializer(lookupArgument);
        }
        return new SpecificDeserializer<>(argument, deserializer);
    }

    private static <T> Argument<T> normalizeLookupArgument(Argument<T> argument) {
        if (argument.getType() != Iterable.class) {
            return argument;
        }
        Argument<?>[] typeParameters = argument.getTypeParameters();
        if (typeParameters.length == 0) {
            return (Argument<T>) Argument.of(Collection.class, argument.getName(), argument.getAnnotationMetadata());
        }
        return (Argument<T>) Argument.of(Collection.class, argument.getName(), argument.getAnnotationMetadata(), typeParameters);
    }

    private boolean isIgnored(AnnotationMetadata annotationMetadata) {
        return !SerdePropertyAccess.canDeserialize(annotationMetadata)
            || annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.IGNORED).orElse(false)
            || annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.IGNORED_DESERIALIZATION).orElse(false);
    }

    private boolean isIgnored(BeanWriteProperty<T, Object> beanProperty, AnnotationMetadata annotationMetadata) {
        return !SerdePropertyAccess.canDeserialize(beanProperty, annotationMetadata)
            || annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.IGNORED).orElse(false)
            || annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.IGNORED_DESERIALIZATION).orElse(false);
    }

    private static void addUnwrappedKeys(List<String> keys,
                                         DerProperty<?, Object> @Nullable [] unwrappedProperties,
                                         boolean acceptCaseInsensitiveProperties) {
        if (unwrappedProperties != null) {
            for (DerProperty<?, Object> unwrappedProperty : unwrappedProperties) {
                DeserBean<?> unwrapped = Objects.requireNonNull(unwrappedProperty.unwrapped);
                for (String propertyKeyName : unwrapped.propertyKeyNames) {
                    PropertiesBag.addKey(keys, propertyKeyName, acceptCaseInsensitiveProperties);
                }
            }
        }
    }

    private static void addSubtypeKeys(List<String> keys,
                                       @Nullable DeserBeanSubtypeInfo<?> subtypeInfo,
                                       boolean acceptCaseInsensitiveProperties) {
        if (subtypeInfo != null) {
            for (DeserBeanSubtypeInfo.SubtypeDef<?> subtype : subtypeInfo.subtypes().values()) {
                addSubtypeKeys(keys, subtype, acceptCaseInsensitiveProperties);
            }
            addSubtypeKeys(keys, subtypeInfo.defaultType(), acceptCaseInsensitiveProperties);
        }
    }

    private static void addSubtypeKeys(List<String> keys,
                                       DeserBeanSubtypeInfo.@Nullable SubtypeDef<?> subtype,
                                       boolean acceptCaseInsensitiveProperties) {
        if (subtype != null) {
            DeserBean<?> subtypeBean = subtype.deserBean();
            if (subtypeBean != null && subtypeBean.propertyKeyNames != null) {
                for (String propertyKeyName : subtypeBean.propertyKeyNames) {
                    PropertiesBag.addKey(keys, propertyKeyName, acceptCaseInsensitiveProperties);
                }
            }
        }
    }

    private record SpecificDeserializer<T>(Argument<T> argument, Deserializer<T> deserializer) {
    }

    private static final class IgnoredPropertyKeys {

        private final int[] keyIndexes;

        private IgnoredPropertyKeys(int[] keyIndexes) {
            this.keyIndexes = keyIndexes;
        }

        @Nullable
        private static IgnoredPropertyKeys create(@Nullable Set<String> ignoredProperties,
                                                  List<String> propertyKeyNames,
                                                  boolean acceptCaseInsensitiveProperties) {
            if (ignoredProperties == null) {
                return null;
            }
            int[] keyIndexes = new int[ignoredProperties.size()];
            int i = 0;
            for (String ignoredProperty : ignoredProperties) {
                keyIndexes[i++] = PropertiesBag.addKey(propertyKeyNames, ignoredProperty, acceptCaseInsensitiveProperties);
            }
            return new IgnoredPropertyKeys(keyIndexes);
        }

        private boolean contains(int keyIndex) {
            for (int ignoredKeyIndex : keyIndexes) {
                if (ignoredKeyIndex == keyIndex) {
                    return true;
                }
            }
            return false;
        }
    }

    static final class AnySetter {
        // CHECKSTYLE:OFF
        final Argument<Object> valueType;
        @Nullable
        private final BiConsumer<Object, Map<?, ?>> mapSetter;
        @Nullable
        private final TriConsumer<Object, Object> valueSetter;

        // Null when DeserBean not initialized
        @Nullable
        public Deserializer<?> deserializer;

        public final boolean constructorArgument;
        public final boolean xmlAnyAttribute;
        public final boolean xmlAnyAttributeQName;
        public final boolean xmlAnyElement;
        // CHECKSTYLE:ON

        private AnySetter(BeanMethod<Object, ?> anySetter) {
            final Argument<?>[] arguments = anySetter.getArguments();
            // if the argument length is 1 we are dealing with a map parameter
            // otherwise we are dealing with 2 parameter variant
            final boolean singleArg = arguments.length == 1;
            this.valueType = (Argument<Object>) (singleArg ? arguments[0].getTypeVariable("V").orElse(Argument.OBJECT_ARGUMENT) : arguments[1]);
            if (singleArg) {
                this.valueSetter = null;
                this.mapSetter = (object, map) -> anySetter.invoke(object, map);
            } else {
                this.valueSetter = (object, key, value) -> anySetter.invoke(object, key, value);
                this.mapSetter = null;
            }
            constructorArgument = false;
            xmlAnyAttribute = anySetter.getAnnotationMetadata().booleanValue(SerdeConfig.class, SerdeConfig.XML_ANY_ATTRIBUTE_PROPERTY).orElse(false);
            xmlAnyAttributeQName = xmlAnyAttribute;
            xmlAnyElement = anySetter.getAnnotationMetadata().booleanValue(SerdeConfig.class, SerdeConfig.XML_ANY_ELEMENT_PROPERTY).orElse(false);
        }

        private AnySetter(BeanWriteProperty<Object, Object> anySetter) {
            // if the argument length is 1 we are dealing with a map parameter
            // otherwise we are dealing with 2 parameter variant
            this.valueType = (Argument<Object>) anySetter.asArgument().getTypeVariable("V").orElse(Argument.OBJECT_ARGUMENT);
            this.mapSetter = anySetter::set;
            this.valueSetter = null;
            this.constructorArgument = false;
            xmlAnyAttribute = anySetter.getAnnotationMetadata().booleanValue(SerdeConfig.class, SerdeConfig.XML_ANY_ATTRIBUTE_PROPERTY).orElse(false);
            xmlAnyAttributeQName = xmlAnyAttribute;
            xmlAnyElement = anySetter.getAnnotationMetadata().booleanValue(SerdeConfig.class, SerdeConfig.XML_ANY_ELEMENT_PROPERTY).orElse(false);
        }

        private AnySetter(Argument<Object> anySetter, int index) {
            // if the argument length is 1 we are dealing with a map parameter
            // otherwise we are dealing with 2 parameter variant
            this.valueType = (Argument<Object>) anySetter.getTypeVariable("V").orElse(Argument.OBJECT_ARGUMENT);
            this.mapSetter = (o, map) -> ((Object[]) o)[index] = map;
            this.valueSetter = null;
            this.constructorArgument = true;
            xmlAnyAttribute = anySetter.getAnnotationMetadata().booleanValue(SerdeConfig.class, SerdeConfig.XML_ANY_ATTRIBUTE_PROPERTY).orElse(false);
            xmlAnyAttributeQName = xmlAnyAttribute;
            xmlAnyElement = anySetter.getAnnotationMetadata().booleanValue(SerdeConfig.class, SerdeConfig.XML_ANY_ELEMENT_PROPERTY).orElse(false);
        }

        void bind(Map<?, Object> values, Object object) {
            if (values != null) {
                if (mapSetter != null) {
                    mapSetter.accept(object, values);
                } else if (valueSetter != null) {
                    for (Object key : values.keySet()) {
                        String s = String.valueOf(key);
                        valueSetter.accept(object, s, values.get(key));
                    }
                }
            }
        }
    }

    private interface TriConsumer<T, V> {
        void accept(T t, String k, @Nullable V v);
    }

    /**
     * Models a deserialization property.
     *
     * @param <B> The bean type
     * @param <P> The property type
     */
    @Internal
    // CHECKSTYLE:OFF
    public static final class DerProperty<B, P> {
        public final BeanIntrospection<B> introspection;
        public final int index;
        public final Argument<P> argument;
        @Nullable
        public final P defaultValue;
        public final boolean mustSetField;
        public final boolean mustSetFieldForConstructor;
        public final boolean explicitlyRequired;
        public final boolean explicitlyRequiredForConstructor;
        public final boolean failOnNullForPrimitives;
        public final boolean nonNull;
        public final boolean nullable;
        public final boolean primitive;
        public final boolean rejectsNullValue;
        public final boolean isAnySetter;
        @Nullable
        public final Class<?> @Nullable [] views;
        @Nullable
        public final String @Nullable [] aliases;

        @Nullable
        public final UnsafeBeanWriteProperty<B, P> beanProperty;
        @Nullable
        public final BeanReadProperty<B, P> beanReadProperty;

        @Nullable
        public final DeserBean<P> unwrapped;
        @Nullable
        public final DerProperty<?, ?> unwrappedProperty;
        @Nullable
        public final String managedRef;
        @Nullable
        public final String backRef;
        /**
         * How the property references beans by their document-scoped identifier, if at all.
         */
        public final SerdeConfig.@Nullable IdReference idReference;
        public final boolean ignored;
        public final boolean xmlUseWrapping;
        public final boolean xmlWrappingConfigured;
        public final boolean xmlAttributeProperty;
        public final boolean xmlTextProperty;
        public final boolean xmlCDataProperty;
        public final boolean xmlListProperty;
        public final boolean xmlMixedProperty;
        public final @Nullable String xmlNamespace;
        public final @Nullable String xmlWrapperName;
        public final @Nullable String xmlWrapperNamespace;
        public final @Nullable String xmlDefaultValue;
        public final @Nullable Boolean xmlNillable;
        public final @Nullable Boolean xmlWrapperNillable;
        @Nullable
        public final String unresolvedTypeVariableName;
        @Nullable
        public final FormatConfiguration format;
        public final Set<DeserializationConfiguration.Feature> featuresWith;
        public final Set<DeserializationConfiguration.Feature> featuresWithout;
        public final boolean hasFeatureOverrides;
        public final boolean merge;

        // Null when DeserBean not initialized
        @Nullable
        public Deserializer<P> deserializer;
        @Nullable
        public Deserializer<P> mergeDeserializer;
        private byte decoderValueKind = DecoderValueKind.NONE_CODE;

        DerProperty(ConversionService conversionService,
                    BeanIntrospection<B> introspection,
                    int index,
                    String property,
                    Argument<P> argument,
                    @Nullable BeanWriteProperty<B, P> beanProperty,
                    @Nullable BeanMethod<B, P> beanMethod,
                    @Nullable DeserBean<P> unwrapped,
                    @Nullable DerProperty<?, ?> unwrappedProperty,
                    boolean ignored,
                    DeserializationConfiguration deserializationConfiguration,
                    boolean failOnNullForPrimitives,
                    @Nullable String unresolvedTypeVariableName) throws SerdeException {
            this(conversionService,
                introspection,
                index,
                property,
                argument,
                argument.getAnnotationMetadata(),
                beanProperty,
                beanMethod,
                unwrapped,
                unwrappedProperty,
                ignored,
                deserializationConfiguration,
                failOnNullForPrimitives,
                unresolvedTypeVariableName
            );
        }

        DerProperty(ConversionService conversionService,
                    BeanIntrospection<B> introspection,
                    int index,
                    String property,
                    Argument<P> argument,
                    AnnotationMetadata argumentMetadata,
                    @Nullable BeanWriteProperty<B, P> beanProperty,
                    @Nullable BeanMethod<B, P> beanMethod,
                    @Nullable DeserBean<P> unwrapped,
                    @Nullable DerProperty<?, ?> unwrappedProperty,
                    boolean ignored,
                    DeserializationConfiguration deserializationConfiguration,
                    boolean failOnNullForPrimitives,
                    @Nullable String unresolvedTypeVariableName) throws SerdeException {
            this.introspection = introspection;
            this.index = index;
            this.ignored = ignored;
            this.unresolvedTypeVariableName = unresolvedTypeVariableName;
            AnnotationMetadata annotationMetadata = resolveArgumentMetadata(introspection, argument, argumentMetadata);
            this.argument = annotationMetadata.isEmpty() ? argument : argument.withAnnotationMetadata(annotationMetadata);
            this.merge = annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.MERGE).orElse(false);
            this.failOnNullForPrimitives = failOnNullForPrimitives;
            FormatConfiguration propertyFormat = FormatConfiguration.from(annotationMetadata);
            if (propertyFormat == null) {
                FormatConfiguration beanFormat = FormatConfiguration.from(introspection.getAnnotationMetadata());
                if (beanFormat != null && switch (beanFormat.shape()) {
                    case ARRAY, OBJECT, POJO -> false;
                    default -> true;
                }) {
                    propertyFormat = beanFormat;
                }
            }
            this.format = propertyFormat;
            this.featuresWith = SerdeFeatures.deserializationFeaturesWith(annotationMetadata);
            this.featuresWithout = SerdeFeatures.deserializationFeaturesWithout(annotationMetadata);
            this.hasFeatureOverrides = !featuresWith.isEmpty() || !featuresWithout.isEmpty();
            Class<?> type = this.argument.getType();
            this.nonNull = this.argument.isNonNull();
            this.nullable = this.argument.isNullable();
            this.primitive = this.argument.isPrimitive();
            this.rejectsNullValue = (primitive && failOnNullForPrimitives) || (nonNull && !nullable);
            boolean optional = type.equals(Optional.class)
                || type.equals(OptionalLong.class)
                || type.equals(OptionalDouble.class)
                || type.equals(OptionalInt.class);
            this.mustSetField = (nonNull && !nullable) || optional;
            this.mustSetFieldForConstructor = mustSetField || (primitive && !nullable); // Kotlin primitives with defaults can be nullable

            if (beanProperty != null) {
                this.beanProperty = (UnsafeBeanWriteProperty<B, P>) beanProperty;
                this.beanReadProperty = resolveBeanReadProperty(introspection, property, beanProperty);
            } else if (beanMethod != null) {
                this.beanProperty = new BeanMethodAsBeanProperty<>(property, beanMethod);
                this.beanReadProperty = null;
            } else {
                this.beanProperty = null;
                this.beanReadProperty = null;
            }
            this.views = SerdeAnnotationUtil.resolveViews(introspection, annotationMetadata);

            try {
                this.defaultValue = annotationMetadata
                    .stringValue(Bindable.class, "defaultValue")
                    .map(s -> conversionService.convertRequired(s, this.argument))
                    .orElse(null);
            } catch (ConversionErrorException e) {
                throw new SerdeException((index > -1 ? "Constructor Argument" : "Property") + " [" + argument + "] of type [" + introspection.getBeanType().getName() + "] defines an invalid default value", e);
            }
            this.unwrapped = unwrapped;
            this.unwrappedProperty = unwrappedProperty;
            this.isAnySetter = annotationMetadata.isAnnotationPresent(SerdeConfig.SerAnySetter.class);
            final String[] aliases = annotationMetadata.stringValues(SerdeConfig.class, SerdeConfig.ALIASES);
            if (ArrayUtils.isNotEmpty(aliases)) {
                this.aliases = ArrayUtils.concat(aliases, property);
            } else {
                this.aliases = null;
            }
            this.managedRef = annotationMetadata.stringValue(SerdeConfig.SerManagedRef.class)
                .orElse(null);
            this.backRef = annotationMetadata.stringValue(SerdeConfig.SerBackRef.class)
                .orElse(null);
            this.idReference = annotationMetadata.enumValue(SerdeConfig.class, SerdeConfig.ID_REFERENCE, SerdeConfig.IdReference.class).orElse(null);
            Optional<Boolean> xmlUseWrapping = annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.META_ANNOTATION_PROPERTY);
            this.xmlUseWrapping = xmlUseWrapping.orElse(true);
            this.xmlWrappingConfigured = xmlUseWrapping.isPresent();
            this.xmlAttributeProperty = annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.XML_ATTRIBUTE_PROPERTY).orElse(false);
            this.xmlTextProperty = annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.XML_TEXT_PROPERTY).orElse(false);
            this.xmlCDataProperty = annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.XML_CDATA_PROPERTY).orElse(false);
            this.xmlListProperty = annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.XML_LIST_PROPERTY).orElse(false);
            this.xmlMixedProperty = annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.XML_MIXED_PROPERTY).orElse(false);
            this.xmlNamespace = annotationMetadata.stringValue(SerdeConfig.class, SerdeConfig.XML_NAMESPACE).orElse(null);
            this.xmlWrapperName = annotationMetadata.stringValue(SerdeConfig.class, SerdeConfig.WRAPPER_PROPERTY).orElse(null);
            this.xmlWrapperNamespace = annotationMetadata.stringValue(SerdeConfig.class, SerdeConfig.XML_WRAPPER_NAMESPACE).orElse(null);
            this.xmlDefaultValue = annotationMetadata.stringValue(SerdeConfig.class, SerdeConfig.XML_DEFAULT_VALUE).orElse(null);
            this.xmlNillable = annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.XML_NILLABLE).orElse(null);
            this.xmlWrapperNillable = annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.XML_WRAPPER_NILLABLE).orElse(null);
            this.explicitlyRequired = annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.REQUIRED)
                .orElse(false);
            this.explicitlyRequiredForConstructor = explicitlyRequired || deserializationConfiguration.isRequireAllCreatorParameters();
        }

        @SuppressWarnings("unchecked")
        @Nullable
        private static <B, P> BeanReadProperty<B, P> resolveBeanReadProperty(BeanIntrospection<B> introspection,
                                                                             String property,
                                                                             BeanWriteProperty<B, P> beanProperty) {
            if (beanProperty instanceof BeanReadProperty<?, ?> beanReadProperty) {
                return (BeanReadProperty<B, P>) beanReadProperty;
            }
            return (BeanReadProperty<B, P>) introspection.getProperty(property).orElse(null);
        }

        @SuppressWarnings("NullAway")
        public void setDefaultPropertyValue(Deserializer.DecoderContext decoderContext, B bean) throws SerdeException {
            decoderContext = resolveFeatures(decoderContext);
            if (explicitlyRequired) {
                throw new SerdeException("Unable to deserialize type [" + introspection.getBeanType().getName() + "]. Required property [" + argument +
                    "] is not present in supplied data");
            }
            P value = provideDefaultValue(decoderContext);
            if (value != null) {
                setPropertyValue(bean, value);
            }
        }

        public void setDefaultConstructorValue(Deserializer.DecoderContext decoderContext, Object[] params) throws SerdeException {
            decoderContext = resolveFeatures(decoderContext);
            params[index] = provideDefaultConstructorValue(decoderContext);
        }

        @Nullable
        private P provideDefaultConstructorValue(Deserializer.DecoderContext decoderContext) throws SerdeException {
            if (explicitlyRequiredForConstructor) {
                throw new SerdeException("Unable to deserialize type [" + introspection.getBeanType().getName() + "]. Required constructor parameter [" + argument + "] at index [" + index + "] is not present or is null in the supplied data");
            }
            return provideDefaultValue(decoderContext, mustSetFieldForConstructor);
        }

        @SuppressWarnings("NullAway")
        public void set(Deserializer.DecoderContext decoderContext, B obj, @Nullable P value) throws SerdeException {
            if (value == null) {
                setDefaultPropertyValue(decoderContext, obj);
            } else {
                setPropertyValue(obj, value);
            }
        }

        @SuppressWarnings("NullAway")
        public void deserializeAndSetConstructorValue(Decoder objectDecoder, Deserializer.DecoderContext decoderContext, Object[] values) throws IOException {
            values[index] = deserializeConstructorValue(deserializer, objectDecoder, decoderContext);
        }

        @SuppressWarnings("NullAway")
        void deserializeAndSetPropertyValue(Decoder objectDecoder, Deserializer.DecoderContext decoderContext, B beanInstance) throws IOException {
            deserializeAndSetPropertyValue(deserializer, objectDecoder, decoderContext, beanInstance);
        }

        @SuppressWarnings("NullAway")
        void deserializeAndSetSimplePropertyValue(Decoder objectDecoder, Deserializer.DecoderContext decoderContext, B beanInstance) throws IOException {
            if (!rejectsNullValue || decoderValueKind == DecoderValueKind.NONE_CODE) {
                deserializeAndSetPropertyValue(objectDecoder, decoderContext, beanInstance);
                return;
            }
            if (primitive) {
                deserializeAndSetDirectPrimitivePropertyValue(objectDecoder, beanInstance);
                return;
            }
            P value;
            try {
                value = deserializeDirectNonNullValue(objectDecoder);
            } catch (Exception e) {
                throw convertPropertyException(e);
            }
            try {
                setPropertyValue(beanInstance, value);
            } catch (Exception e) {
                throw convertException(e, true); // Only convert exceptions from `setUnsafe`
            }
        }

        @SuppressWarnings("NullAway")
        public void deserializeAndSetPropertyValue(Deserializer<P> deserializer,
                                                   Decoder objectDecoder,
                                                   Deserializer.DecoderContext decoderContext,
                                                   B beanInstance) throws IOException {
            if (merge && beanReadProperty != null && decoderValueKind == DecoderValueKind.NONE_CODE) {
                deserializeAndMergePropertyValue(deserializer, objectDecoder, decoderContext, beanInstance);
                return;
            }
            if (primitive && failOnNullForPrimitives && decoderValueKind != DecoderValueKind.NONE_CODE) {
                deserializeAndSetDirectPrimitivePropertyValue(objectDecoder, beanInstance);
                return;
            }
            try {
                P value;
                if (primitive && !failOnNullForPrimitives) {
                    if (decoderValueKind == DecoderValueKind.NONE_CODE) {
                        if (objectDecoder.decodeNull()) {
                            return;
                        }
                        value = deserializePrimitiveValueAfterNullCheck(deserializer, objectDecoder, decoderContext);
                    } else {
                        value = deserializeDirectNullableValue(objectDecoder);
                        if (value == null) {
                            return;
                        }
                        setPropertyValue(beanInstance, value);
                        return;
                    }
                } else {
                    value = deserializeValue(deserializer, objectDecoder, decoderContext);
                }
                setPropertyValue(beanInstance, value);
            } catch (Exception e) {
                throw convertException(e, true); // Only convert exceptions from `setUnsafe`
            }
        }

        @SuppressWarnings({"NullAway"})
        private void deserializeAndMergePropertyValue(Deserializer<P> deserializer,
                                                      Decoder objectDecoder,
                                                      Deserializer.DecoderContext decoderContext,
                                                      B beanInstance) throws IOException {
            try {
                decoderContext = resolveFeatures(decoderContext);
                if (objectDecoder.decodeNull()) {
                    setNullPropertyValue(beanInstance);
                    return;
                }
                P currentValue = beanReadProperty.get(beanInstance);
                if (currentValue == null) {
                    P value = deserializeValue(deserializer, objectDecoder, decoderContext);
                    setPropertyValue(beanInstance, value);
                    return;
                }
                if (argument.getType().isArray()) {
                    P incomingValue = deserializeValue(deserializer, objectDecoder, decoderContext);
                    if (incomingValue == null) {
                        beanProperty.setUnsafe(beanInstance, null);
                    } else {
                        setPropertyValue(beanInstance, concatenateArrays(currentValue, incomingValue));
                    }
                    return;
                }
                Deserializer<P> effectiveDeserializer = mergeDeserializer == null ? deserializer : mergeDeserializer;
                if (effectiveDeserializer instanceof UpdatingDeserializer<P> updatingDeserializer) {
                    updatingDeserializer.deserializeInto(objectDecoder, decoderContext, argument, currentValue);
                } else {
                    P value = deserializeValue(deserializer, objectDecoder, decoderContext);
                    setPropertyValue(beanInstance, value);
                }
            } catch (Exception e) {
                throw convertException(e, true); // Only convert exceptions from `setUnsafe`
            }
        }

        @SuppressWarnings("NullAway")
        private void setNullPropertyValue(B beanInstance) throws SerdeException {
            if (explicitlyRequired) {
                throw new SerdeException("Unable to deserialize type [" + introspection.getBeanType().getName() + "]. Required property [" + argument +
                    "] is not present or is null in the supplied data");
            }
            if (primitive && !failOnNullForPrimitives) {
                return;
            }
            if (rejectsNullValue) {
                throw GeneratedSerdeExceptionUtil.nullValue(Argument.of(introspection.getBeanType()), argument);
            }
            beanProperty.setUnsafe(beanInstance, null);
        }

        @SuppressWarnings("unchecked")
        private P concatenateArrays(P currentValue, P incomingValue) {
            int currentLength = Array.getLength(currentValue);
            int incomingLength = Array.getLength(incomingValue);
            Object mergedArray = Array.newInstance(currentValue.getClass().getComponentType(), currentLength + incomingLength);
            System.arraycopy(currentValue, 0, mergedArray, 0, currentLength);
            System.arraycopy(incomingValue, 0, mergedArray, currentLength, incomingLength);
            return (P) mergedArray;
        }

        @SuppressWarnings("NullAway")
        public void deserializeAndCallBuilder(Decoder objectDecoder, Deserializer.DecoderContext decoderContext, BeanIntrospection.Builder<B> builder) throws IOException {
            try {
                P value = deserializeValue(deserializer, objectDecoder, decoderContext);
                builder.with(index, argument, value);
            } catch (Exception e) {
                throw convertException(e, true); // Only convert exceptions from `with`
            }
        }

        @Nullable
        P deserializeValue(Deserializer<P> deserializer, Decoder objectDecoder, Deserializer.DecoderContext decoderContext) throws IOException {
            decoderContext = resolveFeatures(decoderContext);
            try {
                P value = decoderValueKind == DecoderValueKind.NONE_CODE
                    ? deserializer.deserializeNullable(objectDecoder, decoderContext, argument)
                    : deserializeDirectValue(objectDecoder);
                if (value != null || nullable) {
                    return value;
                }
                if (explicitlyRequired) {
                    throw new SerdeException("Unable to deserialize type [" + introspection.getBeanType().getName() + "]. Required property [" + argument +
                        "] is not present or is null in the supplied data");
                }
                if (primitive && !failOnNullForPrimitives) {
                    return deserializer.getDefaultValue(decoderContext, argument);
                }
                if (rejectsNullValue) {
                    throw GeneratedSerdeExceptionUtil.nullValue(Argument.of(introspection.getBeanType()), argument);
                }
                return null;
            } catch (Exception e) {
                throw convertException(e, false);
            }
        }

        @SuppressWarnings("unchecked")
        @Nullable
        private P deserializeDirectValue(Decoder objectDecoder) throws IOException {
            return switch (decoderValueKind) {
                case DecoderValueKind.STRING_CODE -> (P) objectDecoder.decodeStringNullable();
                case DecoderValueKind.BOOLEAN_CODE -> (P) objectDecoder.decodeBooleanNullable();
                case DecoderValueKind.BYTE_CODE -> (P) objectDecoder.decodeByteNullable();
                case DecoderValueKind.SHORT_CODE -> (P) objectDecoder.decodeShortNullable();
                case DecoderValueKind.CHAR_CODE -> (P) objectDecoder.decodeCharNullable();
                case DecoderValueKind.INT_CODE -> (P) objectDecoder.decodeIntNullable();
                case DecoderValueKind.LONG_CODE -> (P) objectDecoder.decodeLongNullable();
                case DecoderValueKind.FLOAT_CODE -> (P) objectDecoder.decodeFloatNullable();
                case DecoderValueKind.DOUBLE_CODE -> (P) objectDecoder.decodeDoubleNullable();
                default -> throw new IllegalStateException("Unsupported decoder value kind: " + decoderValueKind);
            };
        }

        P deserializePrimitiveValueAfterNullCheck(Deserializer<P> deserializer, Decoder objectDecoder, Deserializer.DecoderContext decoderContext) throws IOException {
            if (decoderValueKind == DecoderValueKind.NONE_CODE) {
                return Objects.requireNonNull(deserializeValue(deserializer, objectDecoder, decoderContext), "Primitive deserializer returned null");
            }
            try {
                return deserializeDirectPrimitiveValue(objectDecoder);
            } catch (Exception e) {
                throw convertException(e, false);
            }
        }

        @SuppressWarnings("unchecked")
        private P deserializeDirectPrimitiveValue(Decoder objectDecoder) throws IOException {
            return switch (decoderValueKind) {
                case DecoderValueKind.BOOLEAN_CODE -> (P) Boolean.valueOf(objectDecoder.decodeBoolean());
                case DecoderValueKind.BYTE_CODE -> (P) Byte.valueOf(objectDecoder.decodeByte());
                case DecoderValueKind.SHORT_CODE -> (P) Short.valueOf(objectDecoder.decodeShort());
                case DecoderValueKind.CHAR_CODE -> (P) Character.valueOf(objectDecoder.decodeChar());
                case DecoderValueKind.INT_CODE -> (P) Integer.valueOf(objectDecoder.decodeInt());
                case DecoderValueKind.LONG_CODE -> (P) Long.valueOf(objectDecoder.decodeLong());
                case DecoderValueKind.FLOAT_CODE -> (P) Float.valueOf(objectDecoder.decodeFloat());
                case DecoderValueKind.DOUBLE_CODE -> (P) Double.valueOf(objectDecoder.decodeDouble());
                default -> throw new IllegalStateException("Unsupported primitive decoder value kind: " + decoderValueKind);
            };
        }

        @SuppressWarnings("unchecked")
        private P deserializeDirectNonNullValue(Decoder objectDecoder) throws IOException {
            if (decoderValueKind == DecoderValueKind.STRING_CODE) {
                return (P) objectDecoder.decodeString();
            }
            return deserializeDirectPrimitiveValue(objectDecoder);
        }

        @SuppressWarnings("NullAway")
        private void deserializeAndSetDirectPrimitivePropertyValue(Decoder objectDecoder, B beanInstance) throws IOException {
            switch (decoderValueKind) {
                case DecoderValueKind.BOOLEAN_CODE -> {
                    boolean value;
                    try {
                        value = objectDecoder.decodeBoolean();
                    } catch (Exception e) {
                        throw convertPropertyException(e);
                    }
                    try {
                        beanProperty.setBooleanUnsafe(beanInstance, value);
                    } catch (Exception e) {
                        throw convertException(e, true);
                    }
                }
                case DecoderValueKind.BYTE_CODE -> {
                    byte value;
                    try {
                        value = objectDecoder.decodeByte();
                    } catch (Exception e) {
                        throw convertPropertyException(e);
                    }
                    try {
                        beanProperty.setByteUnsafe(beanInstance, value);
                    } catch (Exception e) {
                        throw convertException(e, true);
                    }
                }
                case DecoderValueKind.SHORT_CODE -> {
                    short value;
                    try {
                        value = objectDecoder.decodeShort();
                    } catch (Exception e) {
                        throw convertPropertyException(e);
                    }
                    try {
                        beanProperty.setShortUnsafe(beanInstance, value);
                    } catch (Exception e) {
                        throw convertException(e, true);
                    }
                }
                case DecoderValueKind.CHAR_CODE -> {
                    char value;
                    try {
                        value = objectDecoder.decodeChar();
                    } catch (Exception e) {
                        throw convertPropertyException(e);
                    }
                    try {
                        beanProperty.setCharUnsafe(beanInstance, value);
                    } catch (Exception e) {
                        throw convertException(e, true);
                    }
                }
                case DecoderValueKind.INT_CODE -> {
                    int value;
                    try {
                        value = objectDecoder.decodeInt();
                    } catch (Exception e) {
                        throw convertPropertyException(e);
                    }
                    try {
                        beanProperty.setIntUnsafe(beanInstance, value);
                    } catch (Exception e) {
                        throw convertException(e, true);
                    }
                }
                case DecoderValueKind.LONG_CODE -> {
                    long value;
                    try {
                        value = objectDecoder.decodeLong();
                    } catch (Exception e) {
                        throw convertPropertyException(e);
                    }
                    try {
                        beanProperty.setLongUnsafe(beanInstance, value);
                    } catch (Exception e) {
                        throw convertException(e, true);
                    }
                }
                case DecoderValueKind.FLOAT_CODE -> {
                    float value;
                    try {
                        value = objectDecoder.decodeFloat();
                    } catch (Exception e) {
                        throw convertPropertyException(e);
                    }
                    try {
                        beanProperty.setFloatUnsafe(beanInstance, value);
                    } catch (Exception e) {
                        throw convertException(e, true);
                    }
                }
                case DecoderValueKind.DOUBLE_CODE -> {
                    double value;
                    try {
                        value = objectDecoder.decodeDouble();
                    } catch (Exception e) {
                        throw convertPropertyException(e);
                    }
                    try {
                        beanProperty.setDoubleUnsafe(beanInstance, value);
                    } catch (Exception e) {
                        throw convertException(e, true);
                    }
                }
                default -> throw new IllegalStateException("Unsupported primitive decoder value kind: " + decoderValueKind);
            }
        }

        @Nullable
        private P deserializeDirectNullableValue(Decoder objectDecoder) throws IOException {
            try {
                return deserializeDirectValue(objectDecoder);
            } catch (Exception e) {
                throw convertException(e, false);
            }
        }

        @SuppressWarnings("NullAway")
        private void setPropertyValue(B beanInstance, P value) {
            if (!primitive) {
                beanProperty.setUnsafe(beanInstance, value);
                return;
            }
            switch (decoderValueKind) {
                case DecoderValueKind.BOOLEAN_CODE -> beanProperty.setBooleanUnsafe(beanInstance, (Boolean) value);
                case DecoderValueKind.BYTE_CODE -> beanProperty.setByteUnsafe(beanInstance, (Byte) value);
                case DecoderValueKind.SHORT_CODE -> beanProperty.setShortUnsafe(beanInstance, (Short) value);
                case DecoderValueKind.CHAR_CODE -> beanProperty.setCharUnsafe(beanInstance, (Character) value);
                case DecoderValueKind.INT_CODE -> beanProperty.setIntUnsafe(beanInstance, (Integer) value);
                case DecoderValueKind.LONG_CODE -> beanProperty.setLongUnsafe(beanInstance, (Long) value);
                case DecoderValueKind.FLOAT_CODE -> beanProperty.setFloatUnsafe(beanInstance, (Float) value);
                case DecoderValueKind.DOUBLE_CODE -> beanProperty.setDoubleUnsafe(beanInstance, (Double) value);
                default -> beanProperty.setUnsafe(beanInstance, value);
            }
        }

        @Nullable
        P deserializeConstructorValue(Deserializer<P> deserializer, Decoder objectDecoder, Deserializer.DecoderContext decoderContext) throws IOException {
            decoderContext = resolveFeatures(decoderContext);
            try {
                P value;
                if (primitive && !failOnNullForPrimitives) {
                    if (decoderValueKind == DecoderValueKind.NONE_CODE) {
                        if (objectDecoder.decodeNull()) {
                            return provideDefaultConstructorValue(decoderContext);
                        }
                        value = deserializer.deserializeNullable(objectDecoder, decoderContext, argument);
                    } else {
                        value = deserializeDirectValue(objectDecoder);
                        if (value == null) {
                            return provideDefaultConstructorValue(decoderContext);
                        }
                    }
                } else {
                    value = decoderValueKind == DecoderValueKind.NONE_CODE
                        ? deserializer.deserializeNullable(objectDecoder, decoderContext, argument)
                        : deserializeDirectValue(objectDecoder);
                }
                if (value != null || nullable) {
                    return value;
                }
                if (explicitlyRequiredForConstructor) {
                    throw new SerdeException("Unable to deserialize type [" + introspection.getBeanType().getName() + "]. Required constructor parameter [" + argument + "] at index [" + index + "] is not present or is null in the supplied data");
                }
                if (primitive && !failOnNullForPrimitives) {
                    return provideDefaultConstructorValue(decoderContext);
                }
                if (rejectsNullValue) {
                    throw new SerdeException("Unable to deserialize type [" + introspection.getBeanType().getName() + "]. Non-null constructor parameter [" + argument + "] at index [" + index + "] is null in the supplied data");
                }
                return provideDefaultConstructorValue(decoderContext);
            } catch (Exception e) {
                throw convertException(e, false);
            }
        }

        @Nullable
        private P provideDefaultValue(Deserializer.DecoderContext decoderContext) throws SerdeException {
            return provideDefaultValue(decoderContext, mustSetField);
        }

        @SuppressWarnings({"NullAway", "unchecked"})
        @Nullable
        private P provideDefaultValue(Deserializer.DecoderContext decoderContext, boolean mustSetField) throws SerdeException {
            P value = defaultValue;
            if (value == null && mustSetField) {
                Deserializer<P> valueDeserializer = deserializer;
                if (valueDeserializer == null) {
                    valueDeserializer = (Deserializer<P>) decoderContext.findDeserializer(argument);
                }
                value = valueDeserializer.getDefaultValue(decoderContext, argument);
            }
            return value;
        }

        private Deserializer.DecoderContext resolveFeatures(Deserializer.DecoderContext decoderContext) {
            return hasFeatureOverrides ? decoderContext.withFeatures(featuresWith, featuresWithout) : decoderContext;
        }

        private SerdeException convertException(Exception e, boolean catchOnlyUnknown) {
            if (catchOnlyUnknown && e instanceof SerdeException serdeException) {
                return serdeException;
            }
            SerdeException serdeException;
            if (e instanceof InvalidFormatException invalidFormatException) {
                InvalidPropertyFormatException invalidPropertyFormatException = new InvalidPropertyFormatException(invalidFormatException, argument);
                invalidPropertyFormatException.getPath().addAll(invalidFormatException.getPath());
                serdeException = invalidPropertyFormatException;
            } else if (e instanceof SerdeException s) {
                serdeException = s;
            } else {
                serdeException = new SerdeException("Error decoding property [" + argument + "] of type [" + introspection.getBeanType() + "]: " + e.getMessage(), e);
            }
            serdeException.getPath().add(ReferencePath.ofProperty(introspection.getBeanType(), argument));
            return serdeException;
        }

        private SerdeException convertPropertyException(Exception e) {
            if (e instanceof NullValueSerdeException) {
                return GeneratedSerdeExceptionUtil.withPropertyPath(e, Argument.of(introspection.getBeanType()), argument);
            }
            return convertException(e, false);
        }

    }

    private static <B, P> AnnotationMetadata resolveArgumentMetadata(BeanIntrospection<B> introspection, Argument<P> argument, AnnotationMetadata annotationMetadata) {
        // records store metadata in the bean property
        final AnnotationMetadata propertyMetadata = introspection.getProperty(argument.getName(), argument.getType())
            .map(BeanProperty::getAnnotationMetadata)
            .orElse(AnnotationMetadata.EMPTY_METADATA);
        return new AnnotationMetadataHierarchy(propertyMetadata, annotationMetadata);
    }

    private static final class BeanMethodAsBeanProperty<B, P> implements UnsafeBeanWriteProperty<B, P> {

        private final String name;
        private final BeanMethod<B, P> beanMethod;
        private final Argument<P> argument;
        private final Class<P> type;

        private BeanMethodAsBeanProperty(String name, BeanMethod<B, P> beanMethod) {
            this.name = name;
            this.beanMethod = beanMethod;
            this.argument = (Argument<P>) beanMethod.getArguments()[0];
            this.type = argument.getType();
        }

        @Override
        public B withValueUnsafe(B bean, @Nullable P value) {
            throw new IllegalStateException("Not supported");
        }

        @Override
        public void setUnsafe(B bean, @Nullable P value) {
            beanMethod.invoke(bean, value);
        }

        @Override
        public BeanIntrospection<B> getDeclaringBean() {
            return beanMethod.getDeclaringBean();
        }

        @Override
        public B withValue(B bean, @Nullable P value) {
            setUnsafe(bean, value);
            return bean;
        }

        @Override
        public void set(B bean, @Nullable P value) {
            setUnsafe(bean, value);
        }

        @Override
        public Class<P> getType() {
            return type;
        }

        @Override
        public Argument<P> asArgument() {
            return argument;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return beanMethod.getAnnotationMetadata();
        }
    }

}
