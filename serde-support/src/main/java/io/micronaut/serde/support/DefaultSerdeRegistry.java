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
package io.micronaut.serde.support;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.qualifiers.MatchArgumentQualifier;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Serde;
import io.micronaut.serde.SerdeIntrospections;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.SerdeBackendMode;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.SerializationConfiguration;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.deserializers.ObjectDeserializer;
import io.micronaut.serde.support.deserializers.SerdeDeserializationPreInstantiateCallback;
import io.micronaut.serde.support.deserializers.collect.CoreCollectionsDeserializers;
import io.micronaut.serde.support.runtime.GeneratedSerdeRuntimeLoader;
import io.micronaut.serde.support.runtime.SerdeBackendModeResolver;
import io.micronaut.serde.support.serdes.ObjectArraySerde;
import io.micronaut.serde.support.serdes.Serdes;
import io.micronaut.serde.support.serializers.CoreSerializers;
import io.micronaut.serde.support.serializers.ObjectSerializer;
import io.micronaut.serde.support.util.TypeKey;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of the {@link io.micronaut.serde.SerdeRegistry} interface.
 */
@Singleton
@BootstrapContextCompatible
public class DefaultSerdeRegistry implements SerdeRegistry {

    private static final String JACKSON_ANNOTATION_PREFIX = "com.fasterxml.jackson.annotation.";

    private final List<BeanDefinition<Serializer>> serializers = new ArrayList<>(100);
    private final List<BeanDefinition<Deserializer>> deserializers = new ArrayList<>(100);
    private final List<BeanDefinition<Serde>> internalSerdes = new ArrayList<>(100);

    // if there is a single Serde that is part of the serializerMap *and* deserializerMap, this can
    // lead to interface type check thrashing. For that reason, we wrap the serializer side with
    // a wrapper object.
    private final Map<TypeKey, SerializerWrapper> serializerMap = new ConcurrentHashMap<>(50);
    private final Map<TypeKey, Deserializer<?>> deserializerMap = new ConcurrentHashMap<>(50);

    private final BeanContext beanContext;
    private final SerdeIntrospections introspections;
    private final ObjectSerializer objectSerializer;
    private final ObjectDeserializer objectDeserializer;
    private final Serde<Object[]> objectArraySerde;
    private final ConversionService conversionService;
    private final SerdeConfiguration serdeConfiguration;
    private final SerdeBackendModeResolver backendModeResolver;
    private final GeneratedSerdeRuntimeLoader generatedSerdeRuntimeLoader;
    private final SerializationConfiguration serializationConfiguration;
    private final DeserializationConfiguration deserializationConfiguration;

    /**
     * Default constructor.
     *
     * @param beanContext                  The bean context
     * @param introspections               The introspections
     * @param conversionService            The conversion service
     * @param serdeConfiguration           The {@link SerdeConfiguration}
     * @param serializationConfiguration   The {@link SerializationConfiguration}
     * @param deserializationConfiguration The {@link DeserializationConfiguration}
     */
    public DefaultSerdeRegistry(
        @Nullable BeanContext beanContext,
        SerdeIntrospections introspections,
        ConversionService conversionService,
        SerdeConfiguration serdeConfiguration,
        SerializationConfiguration serializationConfiguration,
        DeserializationConfiguration deserializationConfiguration) {
        this.serdeConfiguration = serdeConfiguration;
        this.serializationConfiguration = serializationConfiguration;
        this.deserializationConfiguration = deserializationConfiguration;
        this.backendModeResolver = new SerdeBackendModeResolver(serdeConfiguration);
        this.generatedSerdeRuntimeLoader = new GeneratedSerdeRuntimeLoader();
        this.introspections = introspections;
        this.beanContext = beanContext;
        this.conversionService = conversionService;

        registerSerializersDeserializersFromBeanContext(beanContext);
        registerBuiltInSerdes();

        this.objectSerializer = new ObjectSerializer(
            introspections,
            serdeConfiguration,
            serializationConfiguration,
            beanContext);
        this.objectDeserializer = new ObjectDeserializer(introspections,
            deserializationConfiguration,
            serdeConfiguration,
            beanContext == null ? null : beanContext.findBean(SerdeDeserializationPreInstantiateCallback.class).orElse(null)
        );
        this.objectArraySerde = new ObjectArraySerde();
    }

    private void registerSerializersDeserializersFromBeanContext(@Nullable BeanContext beanContext) {
        if (beanContext == null) {
            return;
        }
        for (BeanDefinition<Serializer> serializer : beanContext.getBeanDefinitions(Serializer.class)) {
            if (serializer.getDeclaringType().orElse(null) == LegacyBeansFactory.class) {
                continue;
            }
            final List<Argument<?>> typeArguments = serializer.getTypeArguments(Serializer.class);
            if (CollectionUtils.isEmpty(typeArguments)) {
                throw new ConfigurationException("Serializer without generic types defined: " + serializer.getBeanType());
            }
            final Argument<?> argument = typeArguments.iterator().next();
            if (!argument.equalsType(Argument.OBJECT_ARGUMENT)) {
                serializers.add(serializer);
            }
        }
        for (BeanDefinition<Deserializer> deserializer : beanContext.getBeanDefinitions(Deserializer.class)) {
            if (deserializer.getDeclaringType().orElse(null) == LegacyBeansFactory.class) {
                continue;
            }
            final List<Argument<?>> typeArguments = deserializer.getTypeArguments(Deserializer.class);
            if (CollectionUtils.isEmpty(typeArguments)) {
                throw new ConfigurationException("Deserializer without generic types defined: " + deserializer.getBeanType());
            }
            final Argument<?> argument = typeArguments.iterator().next();
            if (!argument.equalsType(Argument.OBJECT_ARGUMENT)) {
                deserializers.add(deserializer);
            }
        }
    }

    @Override
    public SerdeRegistry cloneWithConfiguration(@Nullable SerdeConfiguration configuration, @Nullable SerializationConfiguration serializationConfiguration, @Nullable DeserializationConfiguration deserializationConfiguration) {
        return new DefaultSerdeRegistry(
            beanContext,
            introspections,
            conversionService,
            configuration == null ? this.serdeConfiguration : configuration,
            serializationConfiguration == null ? this.serializationConfiguration : serializationConfiguration,
            deserializationConfiguration == null ? this.deserializationConfiguration : deserializationConfiguration
        );
    }

    /**
     * Find internal serde by type.
     *
     * @param type The serde type
     * @param <T>  The serde type
     * @return a serde or null
     */
    @Nullable
    @Internal
    public <T> Serde<T> findInternalSerde(Argument<T> type) {
        for (BeanDefinition<Serde> serdeBeanDefinition : internalSerdes) {
            if (serdeBeanDefinition instanceof InternalSerdeBeanDefinition<?> internalSerdeBeanDefinition
                && internalSerdeBeanDefinition.typeArgument.isAssignableFrom(type)) {
                return (Serde<T>) internalSerdeBeanDefinition.value;
            }
        }
        return null;
    }

    private void registerBuiltInSerdes() {
        Serdes.register(serdeConfiguration, introspections, serdeRegistrar -> {
            try {
                for (Argument<?> type : serdeRegistrar.getTypes()) {
                    deserializers.add(new InternalSerdeBeanDefinition<>(type, Deserializer.class, serdeRegistrar, serdeRegistrar.getOrder()));
                    serializers.add(new InternalSerdeBeanDefinition<>(type, Serializer.class, serdeRegistrar, serdeRegistrar.getOrder()));
                    internalSerdes.add(new InternalSerdeBeanDefinition<>(type, Serde.class, serdeRegistrar, serdeRegistrar.getOrder()));
                }
            } catch (NoClassDefFoundError ignore) {
                // Might be a missing sql module
            }
        });
        CoreCollectionsDeserializers.register(conversionService, deserializerRegistrar -> {
            for (Argument<?> type : deserializerRegistrar.getTypes()) {
                deserializers.add(new InternalSerdeBeanDefinition<>(type, Deserializer.class, deserializerRegistrar, deserializerRegistrar.getOrder()));
            }
        });
        CoreSerializers.register(serializationConfiguration, serializerRegistrar -> {
            for (Argument<?> type : serializerRegistrar.getTypes()) {
                serializers.add(new InternalSerdeBeanDefinition<>(type, Serializer.class, serializerRegistrar, serializerRegistrar.getOrder()));
            }
        });
    }

    @Override
    public <T, D extends Serializer<? extends T>> D findCustomSerializer(Class<? extends D> serializerClass) throws SerdeException {
        checkBeanContext();
        return beanContext.findBean(serializerClass).orElseThrow(() -> new SerdeException("Cannot find serializer: " + serializerClass));
    }

    @Override
    public <T, D extends Deserializer<? extends T>> D findCustomDeserializer(Class<? extends D> deserializerClass) throws SerdeException {
        checkBeanContext();
        return beanContext.findBean(deserializerClass).orElseThrow(() -> new SerdeException("Cannot find deserializer: " + deserializerClass));
    }

    @Override
    public <D extends PropertyNamingStrategy> D findNamingStrategy(Class<? extends D> namingStrategyClass) throws SerdeException {
        checkBeanContext();
        return beanContext.findBean(namingStrategyClass).orElseThrow(() -> new SerdeException("Cannot find naming strategy: " + namingStrategyClass));
    }

    private void checkBeanContext() throws SerdeException {
        if (beanContext == null) {
            throw new SerdeException("No bean context present!");
        }
    }

    @Override
    public <T> Deserializer<? extends T> findDeserializer(Argument<? extends T> type) throws SerdeException {
        Objects.requireNonNull(type, "Type cannot be null");
        final TypeKey key = new TypeKey(type);
        final boolean contextualType = hasContextualArgumentMetadata(type);
        if (!contextualType) {
            final Deserializer<?> deserializer = deserializerMap.get(key);
            if (deserializer != null) {
                return (Deserializer<? extends T>) deserializer;
            }
        }
        if (type.getType().equals(Object.class)) {
            return (Deserializer<? extends T>) objectDeserializer;
        }
        if (type.getType().equals(Object[].class)) {
            return (Deserializer<? extends T>) objectArraySerde;
        }

        Deserializer<?> deser = resolveBeanDeserializer(type, matchDeserializerCandidates(type, deserializers));
        if (deser != null) {
            if (!contextualType) {
                deserializerMap.put(key, deser);
            }
            return (Deserializer<? extends T>) deser;
        }
        if (key.getType().isArray()) {
            if (!contextualType) {
                deserializerMap.put(key, objectArraySerde);
            }
            return (Deserializer<? extends T>) objectArraySerde;
        }

        BeanIntrospection<?> deserializableIntrospection = getDeserializableIntrospection(type);
        AnnotationMetadata annotationMetadata = deserializableIntrospection == null ? null : deserializableIntrospection.getAnnotationMetadata();
        SerdeBackendMode backendMode = backendModeResolver.resolveDeserializationMode(annotationMetadata);
        Deserializer<?> generatedDeserializer = contextualType ? null : resolveGeneratedDeserializer(type, deserializableIntrospection, backendMode);
        if (generatedDeserializer != null) {
            if (!contextualType) {
                deserializerMap.put(key, generatedDeserializer);
            }
            return (Deserializer<? extends T>) generatedDeserializer;
        }

        if (!contextualType) {
            deserializerMap.put(key, objectDeserializer);
        }
        return (Deserializer<? extends T>) objectDeserializer;
    }

    private <T> T getBean(BeanDefinition<T> definition) {
        if (definition instanceof InternalSerdeBeanDefinition<?> internalSerdeBeanDefinition) {
            return (T) internalSerdeBeanDefinition.value;
        }
        return beanContext.getBean(definition);
    }

    @Override
    public <T> Collection<BeanIntrospection<? extends T>> getDeserializableSubtypes(Class<T> superType) {
        return introspections.findSubtypeDeserializables(superType);
    }

    @Override
    public <T> Serializer<? super T> findSerializer(Argument<? extends T> type) throws SerdeException {
        Objects.requireNonNull(type, "Type cannot be null");
        final TypeKey key = new TypeKey(type);
        final boolean contextualType = hasContextualArgumentMetadata(type);
        if (!contextualType) {
            SerializerWrapper wrapper = serializerMap.get(key);
            if (wrapper != null) {
                return (Serializer<? super T>) wrapper.serializer;
            }
        }
        if (type.getType().equals(Object.class)) {
            return objectSerializer;
        }
        if (type.getType().equals(Object[].class)) {
            return (Serializer<? super T>) objectArraySerde;
        }

        Serializer<?> ser = resolveBeanSerializer(type, matchSerializerCandidates(type, serializers));
        if (ser != null) {
            if (!contextualType) {
                serializerMap.put(key, new SerializerWrapper(ser));
            }
            return (Serializer<? super T>) ser;
        }
        if (key.getType().isArray()) {
            if (!contextualType) {
                serializerMap.put(key, new SerializerWrapper(objectArraySerde));
            }
            return (Serializer<? super T>) objectArraySerde;
        }

        BeanIntrospection<?> serializableIntrospection = getSerializableIntrospection(type);
        AnnotationMetadata annotationMetadata = serializableIntrospection == null ? null : serializableIntrospection.getAnnotationMetadata();
        SerdeBackendMode backendMode = backendModeResolver.resolveSerializationMode(annotationMetadata);
        Serializer<?> generatedSerializer = contextualType ? null : resolveGeneratedSerializer(type, serializableIntrospection, backendMode);
        if (generatedSerializer != null) {
            if (!contextualType) {
                serializerMap.put(key, new SerializerWrapper(generatedSerializer));
            }
            return (Serializer<? super T>) generatedSerializer;
        }

        if (!contextualType) {
            serializerMap.put(key, new SerializerWrapper(objectSerializer));
        }
        return objectSerializer;
    }

    private <T> Collection<BeanDefinition<Deserializer>> matchDeserializerCandidates(Argument<?> type,
                                                                                     Collection<BeanDefinition<Deserializer>> pool) {
        if (pool == null || pool.isEmpty()) {
            return List.of();
        }
        Collection<BeanDefinition<Deserializer>> filtered = filterLegacyBeansFactoryCandidates(pool);
        if (filtered.isEmpty()) {
            return List.of();
        }
        return deduplicateBeanDefinitions(MatchArgumentQualifier.covariant(Deserializer.class, type).filter(Deserializer.class, filtered));
    }

    private <T> Deserializer<?> resolveBeanDeserializer(Argument<? extends T> type,
                                                       Collection<BeanDefinition<Deserializer>> candidates) throws SerdeException {
        if (CollectionUtils.isEmpty(candidates)) {
            return null;
        }
        if (candidates.size() == 1) {
            return getBean(candidates.iterator().next());
        }
        return getBean(lastChanceResolveDeserializer(type, candidates));
    }

    private <T> Collection<BeanDefinition<Serializer>> matchSerializerCandidates(Argument<?> type,
                                                                                 Collection<BeanDefinition<Serializer>> pool) {
        if (pool == null || pool.isEmpty()) {
            return List.of();
        }
        Collection<BeanDefinition<Serializer>> filtered = filterLegacyBeansFactoryCandidates(pool);
        if (filtered.isEmpty()) {
            return List.of();
        }
        return deduplicateBeanDefinitions(MatchArgumentQualifier.contravariant(Serializer.class, type).filter(Serializer.class, filtered));
    }

    private <T> Collection<BeanDefinition<T>> filterLegacyBeansFactoryCandidates(Collection<BeanDefinition<T>> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return candidates.stream()
            .filter(candidate -> candidate.getDeclaringType().orElse(null) != LegacyBeansFactory.class)
            .toList();
    }

    private <T> Collection<BeanDefinition<T>> deduplicateBeanDefinitions(Collection<BeanDefinition<T>> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        Map<Class<?>, BeanDefinition<T>> unique = new LinkedHashMap<>();
        for (BeanDefinition<T> candidate : candidates) {
            unique.putIfAbsent(candidate.getBeanType(), candidate);
        }
        return List.copyOf(unique.values());
    }

    private <T> Serializer<?> resolveBeanSerializer(Argument<? extends T> type,
                                                   Collection<BeanDefinition<Serializer>> candidates) throws SerdeException {
        if (CollectionUtils.isEmpty(candidates)) {
            return null;
        }
        if (candidates.size() == 1) {
            return getBean(candidates.iterator().next());
        }
        return getBean(lastChanceResolveSerializer(type, candidates));
    }

    private Serializer<?> resolveGeneratedSerializer(Argument<?> type,
                                                      @Nullable BeanIntrospection<?> introspection,
                                                      SerdeBackendMode backendMode) throws SerdeException {
        if (serializationConfiguration.sortPropertiesAlphabetically()) {
            if (backendMode == SerdeBackendMode.GENERATED) {
                throw new SerdeException("Generated serializer backend required for type [" + type + "] but alphabetical property sorting requires introspection backend");
            }
            return null;
        }
        if (backendMode == SerdeBackendMode.INTROSPECTION) {
            return null;
        }
        if (introspection == null) {
            if (backendMode == SerdeBackendMode.GENERATED) {
                throw new SerdeException("Generated serializer backend required for type [" + type + "] but no serializable introspection metadata is available");
            }
            return null;
        }
        if (shouldBypassGeneratedSerializer(introspection)) {
            if (backendMode == SerdeBackendMode.GENERATED) {
                throw new SerdeException("Generated serializer backend required for type [" + type + "] but naming strategy requires introspection backend");
            }
            return null;
        }
        GeneratedSerdeRuntimeLoader.LookupResult<Serializer<?>> result = generatedSerdeRuntimeLoader.loadSerializer(introspection, type);
        if (result.status() == GeneratedSerdeRuntimeLoader.Status.AVAILABLE) {
            return result.value();
        }
        if (backendMode == SerdeBackendMode.GENERATED) {
            String detail = result.message() == null ? "No generated serializer is available for type [" + type + "]" : result.message();
            throw new SerdeException(detail);
        }
        return null;
    }

    private Deserializer<?> resolveGeneratedDeserializer(Argument<?> type,
                                                         @Nullable BeanIntrospection<?> introspection,
                                                         SerdeBackendMode backendMode) throws SerdeException {
        if (backendMode == SerdeBackendMode.INTROSPECTION) {
            return null;
        }
        if (introspection == null) {
            if (backendMode == SerdeBackendMode.GENERATED) {
                throw new SerdeException("Generated deserializer backend required for type [" + type + "] but no deserializable introspection metadata is available");
            }
            return null;
        }
        if (shouldBypassGeneratedDeserializer(introspection)) {
            if (backendMode == SerdeBackendMode.GENERATED) {
                throw new SerdeException("Generated deserializer backend required for type [" + type + "] but naming strategy requires introspection backend");
            }
            return null;
        }
        GeneratedSerdeRuntimeLoader.LookupResult<Deserializer<?>> result = generatedSerdeRuntimeLoader.loadDeserializer(introspection, type);
        if (result.status() == GeneratedSerdeRuntimeLoader.Status.AVAILABLE) {
            return result.value();
        }
        if (backendMode == SerdeBackendMode.GENERATED) {
            String detail = result.message() == null ? "No generated deserializer is available for type [" + type + "]" : result.message();
            throw new SerdeException(detail);
        }
        return null;
    }

    private boolean shouldBypassGeneratedSerializer(BeanIntrospection<?> introspection) {
        if (serdeConfiguration.getPropertyNamingStrategy() != null) {
            return true;
        }
        AnnotationMetadata annotationMetadata = introspection.getAnnotationMetadata();
        if (annotationMetadata.classValue(SerdeConfig.class, SerdeConfig.RUNTIME_NAMING).isPresent()) {
            return true;
        }
        if (!isSourceGenEligible(annotationMetadata, SerdeConfig.SOURCEGEN_SERIALIZER_ELIGIBLE)) {
            return true;
        }
        if (hasCustomSerializerConfiguration(annotationMetadata)) {
            return true;
        }
        for (BeanProperty<?, ?> beanProperty : introspection.getBeanProperties()) {
            AnnotationMetadata propertyMetadata = beanProperty.getAnnotationMetadata();
            if (propertyMetadata.hasAnnotation(SerdeConfig.SerUnwrapped.class)
                || propertyMetadata.hasAnnotation(SerdeConfig.SerIgnored.class)
                || propertyMetadata.hasAnnotation(SerdeConfig.SerIncluded.class)
                || propertyMetadata.hasAnnotation(SerdeConfig.SerValue.class)
                || hasCustomSerializerConfiguration(propertyMetadata)) {
                return true;
            }
            AnnotationMetadata argumentMetadata = beanProperty.asArgument().getAnnotationMetadata();
            if (argumentMetadata.hasAnnotation(SerdeConfig.SerIgnored.class)
                || argumentMetadata.hasAnnotation(SerdeConfig.SerIncluded.class)
                || hasCustomSerializerConfiguration(argumentMetadata)) {
                return true;
            }
            Set<String> visitedTypes = new HashSet<>();
            visitedTypes.add(introspection.getBeanType().getName());
            if (hasIneligibleNestedGeneratedType(beanProperty.asArgument(), visitedTypes)) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldBypassGeneratedDeserializer(BeanIntrospection<?> introspection) {
        if (serdeConfiguration.getPropertyNamingStrategy() != null) {
            return true;
        }
        AnnotationMetadata annotationMetadata = introspection.getAnnotationMetadata();
        if (annotationMetadata.classValue(SerdeConfig.class, SerdeConfig.RUNTIME_NAMING).isPresent()) {
            return true;
        }
        if (!isSourceGenEligible(annotationMetadata, SerdeConfig.SOURCEGEN_DESERIALIZER_ELIGIBLE)) {
            return true;
        }
        if (hasCustomDeserializerConfiguration(annotationMetadata)) {
            return true;
        }
        for (BeanProperty<?, ?> beanProperty : introspection.getBeanProperties()) {
            AnnotationMetadata propertyMetadata = beanProperty.getAnnotationMetadata();
            if (propertyMetadata.hasAnnotation(SerdeConfig.SerUnwrapped.class)
                || propertyMetadata.hasAnnotation(SerdeConfig.SerIgnored.class)
                || propertyMetadata.hasAnnotation(SerdeConfig.SerIncluded.class)
                || hasCustomDeserializerConfiguration(propertyMetadata)) {
                return true;
            }
            AnnotationMetadata argumentMetadata = beanProperty.asArgument().getAnnotationMetadata();
            if (argumentMetadata.hasAnnotation(SerdeConfig.SerIgnored.class)
                || argumentMetadata.hasAnnotation(SerdeConfig.SerIncluded.class)
                || hasCustomDeserializerConfiguration(argumentMetadata)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSourceGenEligible(AnnotationMetadata annotationMetadata, String member) {
        return annotationMetadata.booleanValue(SerdeConfig.class, member).orElse(true);
    }

    private boolean hasCustomSerializerConfiguration(AnnotationMetadata annotationMetadata) {
        return annotationMetadata.classValue(SerdeConfig.class, SerdeConfig.SERIALIZER_CLASS).isPresent();
    }

    private boolean hasCustomDeserializerConfiguration(AnnotationMetadata annotationMetadata) {
        return annotationMetadata.classValue(SerdeConfig.class, SerdeConfig.DESERIALIZER_CLASS).isPresent();
    }

    private boolean hasContextualArgumentMetadata(Argument<?> argument) {
        if (hasContextualMetadata(argument.getAnnotationMetadata())) {
            return true;
        }
        for (Argument<?> typeParameter : argument.getTypeParameters()) {
            if (hasContextualArgumentMetadata(typeParameter)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasContextualMetadata(AnnotationMetadata annotationMetadata) {
        return hasJacksonAnnotationNames(annotationMetadata)
            || annotationMetadata.hasAnnotation(SerdeConfig.SerUnwrapped.class)
            || annotationMetadata.hasAnnotation(SerdeConfig.SerIgnored.class)
            || annotationMetadata.hasAnnotation(SerdeConfig.SerIncluded.class)
            || annotationMetadata.hasAnnotation(SerdeConfig.SerValue.class)
            || annotationMetadata.hasAnnotation(SerdeConfig.META_ANNOTATION_PROPERTY_ORDER)
            || annotationMetadata.classValue(SerdeConfig.class, SerdeConfig.SERIALIZER_CLASS).isPresent()
            || annotationMetadata.classValue(SerdeConfig.class, SerdeConfig.DESERIALIZER_CLASS).isPresent();
    }

    private boolean hasIneligibleNestedGeneratedType(Argument<?> argument, Set<String> visited) {
        Class<?> type = argument.getType();
        if (type.isPrimitive() || type.isArray() || type.isEnum()) {
            return false;
        }
        String typeName = type.getName();
        if (typeName.startsWith("java.") || !visited.add(typeName)) {
            return false;
        }
        try {
            BeanIntrospection<?> nested = introspections.getSerializableIntrospection((Argument<Object>) argument);
            if (!nested.getAnnotationMetadata().booleanValue(SerdeConfig.class, SerdeConfig.SOURCEGEN_SERIALIZER_ELIGIBLE).orElse(true)) {
                return true;
            }
            for (BeanProperty<?, ?> beanProperty : nested.getBeanProperties()) {
                if (hasIneligibleNestedGeneratedType(beanProperty.asArgument(), visited)) {
                    return true;
                }
            }
            return false;
        } catch (IntrospectionException e) {
            return false;
        }
    }

    private boolean hasJacksonAnnotationNames(AnnotationMetadata annotationMetadata) {
        return annotationMetadata.getAnnotationNames().stream().anyMatch(name -> name.startsWith(JACKSON_ANNOTATION_PREFIX));
    }

    private @Nullable <T> BeanIntrospection<?> getSerializableIntrospection(Argument<? extends T> type) {
        try {
            return introspections.getSerializableIntrospection((Argument<T>) type);
        } catch (IntrospectionException e) {
            return null;
        }
    }

    private @Nullable <T> BeanIntrospection<?> getDeserializableIntrospection(Argument<? extends T> type) {
        try {
            return introspections.getDeserializableIntrospection((Argument<T>) type);
        } catch (IntrospectionException e) {
            return null;
        }
    }

    @NonNull
    private <T> BeanDefinition<T> lastChanceResolve(Argument<?> type,
                                                    Collection<BeanDefinition<T>> candidates,
                                                    String beansResolved) throws SerdeException {
        if (candidates.size() > 1) {
            List<BeanDefinition<T>> primary = candidates.stream().filter(BeanDefinition::isPrimary).toList();
            if (!primary.isEmpty()) {
                candidates = primary;
            }
        }
        if (candidates.size() == 1) {
            return candidates.iterator().next();
        }
        candidates = candidates.stream().filter(candidate -> !candidate.hasDeclaredStereotype(Secondary.class)).toList();
        if (candidates.size() == 1) {
            return candidates.iterator().next();
        }
        // pick the bean with the highest priority
        final Iterator<BeanDefinition<T>> i = candidates.stream()
            .sorted((bean1, bean2) -> {
                int order1 = OrderUtil.getOrder(bean1.getAnnotationMetadata());
                int order2 = OrderUtil.getOrder(bean2.getAnnotationMetadata());
                return Integer.compare(order1, order2);
            })
            .iterator();
        if (i.hasNext()) {
            final BeanDefinition<T> bean = i.next();
            if (i.hasNext()) {
                // check there are not 2 beans with the same order
                final BeanDefinition<T> next = i.next();
                if (OrderUtil.getOrder(bean.getAnnotationMetadata()) == OrderUtil.getOrder(next.getAnnotationMetadata())) {
                    throw new SerdeException("Multiple possible " + beansResolved + " found for type [" + type + "]: " + candidates);
                }
            }
            return bean;
        }
        throw new SerdeException("Multiple possible " + beansResolved + " found for type [" + type + "]: " + candidates);
    }

    private BeanDefinition<Serializer> lastChanceResolveSerializer(
        Argument<?> type,
        Collection<BeanDefinition<Serializer>> candidates) throws SerdeException {

        return lastChanceResolve(type, candidates, "serializers");
    }

    private BeanDefinition<Deserializer> lastChanceResolveDeserializer(
        Argument<?> type,
        Collection<BeanDefinition<Deserializer>> candidates) throws SerdeException {

        return lastChanceResolve(type, candidates, "deserializers");
    }

    @Override
    public Serializer.EncoderContext newEncoderContext(Class<?> view) {
        if (view != null && view != Object.class) {
            return new DefaultEncoderContext(this) {
                @Override
                public boolean hasView(Class<?>... views) {
                    for (Class<?> candidate : views) {
                        if (candidate.isAssignableFrom(view)) {
                            return true;
                        }
                    }
                    return false;
                }
            };
        }
        return new DefaultEncoderContext(this);
    }

    @Override
    public Deserializer.DecoderContext newDecoderContext(Class<?> view) {
        if (view != null && view != Object.class) {
            return new DefaultDecoderContext(this) {
                @Override
                public boolean hasView(Class<?>... views) {
                    for (Class<?> candidate : views) {
                        if (candidate.isAssignableFrom(view)) {
                            return true;
                        }
                    }
                    return false;
                }
            };
        }
        return new DefaultDecoderContext(this);
    }

    @Override
    public ConversionService getConversionService() {
        return this.conversionService;
    }

    @Internal
    public final SerdeConfiguration getSerdeConfiguration() {
        return serdeConfiguration;
    }

    @Internal
    final SerializationConfiguration getSerializationConfiguration() {
        return serializationConfiguration;
    }

    @Internal
    final DeserializationConfiguration getDeserializationConfiguration() {
        return deserializationConfiguration;
    }

    private static final class InternalSerdeBeanDefinition<T> implements BeanDefinition<T> {
        private final Argument<?> argument;
        private final Argument<?> typeArgument;
        private final T value;
        private final List<Argument<?>> typeParameters;
        private final AnnotationMetadata annotationMetadata;

        private InternalSerdeBeanDefinition(Argument<?> typeArgument,
                                            Class<T> container,
                                            T value,
                                            int order) {
            this.argument = Argument.of(container, typeArgument);
            this.value = value;
            this.typeArgument = typeArgument;
            this.typeParameters = List.of(argument.getTypeParameters());
            if (order == 0) {
                order = 10; // Assign internal serdes to a lower priority
            }
            MutableAnnotationMetadata mutableAnnotationMetadata = new MutableAnnotationMetadata();
            mutableAnnotationMetadata.addAnnotation(Order.class.getName(), Map.of("value", order));
            annotationMetadata = mutableAnnotationMetadata;
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return annotationMetadata;
        }

        @NonNull
        @Override
        public Argument<T> asArgument() {
            return (Argument<T>) argument;
        }

        @NonNull
        @Override
        public List<Argument<?>> getTypeArguments() {
            return typeParameters;
        }

        @NonNull
        @Override
        public List<Argument<?>> getTypeArguments(Class<?> type) {
            if (type == Serializer.class || type == Deserializer.class) {
                return typeParameters;
            }
            return List.of();
        }

        @Override
        public Class<T> getBeanType() {
            return (Class) Serde.class;
        }

        @Override
        public boolean isEnabled(@NonNull BeanContext context, @Nullable BeanResolutionContext resolutionContext) {
            return true;
        }

        @Override
        public String toString() {
            return argument.getTypeName();
        }

    }

    // Prevent type check thrashing
    private record SerializerWrapper(Serializer<?> serializer) {
    }
}
