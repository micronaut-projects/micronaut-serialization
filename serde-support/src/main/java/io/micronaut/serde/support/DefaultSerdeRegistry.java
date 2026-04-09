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
import io.micronaut.core.annotation.Order;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ParametrizedInstantiatableBeanDefinition;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.qualifiers.MatchArgumentQualifier;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.FormatConfiguration;
import io.micronaut.serde.FormattedDeserializer;
import io.micronaut.serde.FormattedSerializer;
import io.micronaut.serde.Serde;
import io.micronaut.serde.SerdeIntrospections;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.SerializationConfiguration;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.deserializers.ObjectDeserializer;
import io.micronaut.serde.support.deserializers.SerdeDeserializationPreInstantiateCallback;
import io.micronaut.serde.support.deserializers.collect.CoreCollectionsDeserializers;
import io.micronaut.serde.support.serdes.ObjectArraySerde;
import io.micronaut.serde.support.serdes.Serdes;
import io.micronaut.serde.support.serializers.CoreSerializers;
import io.micronaut.serde.support.serializers.ObjectSerializer;
import io.micronaut.serde.support.util.TypeKey;
import io.micronaut.serde.util.CustomizableDeserializer;
import io.micronaut.serde.util.CustomizableSerializer;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of the {@link io.micronaut.serde.SerdeRegistry} interface.
 */
@Singleton
@BootstrapContextCompatible
public class DefaultSerdeRegistry implements SerdeRegistry {

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
        BeanContext beanContext,
        SerdeIntrospections introspections,
        ConversionService conversionService,
        SerdeConfiguration serdeConfiguration,
        SerializationConfiguration serializationConfiguration,
        DeserializationConfiguration deserializationConfiguration) {
        this.serdeConfiguration = serdeConfiguration;
        this.serializationConfiguration = serializationConfiguration;
        this.deserializationConfiguration = deserializationConfiguration;
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
            beanContext.findBean(SerdeDeserializationPreInstantiateCallback.class).orElse(null)
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
        return cloneWithConfiguration(configuration, serializationConfiguration, deserializationConfiguration, introspections);
    }

    @Override
    public SerdeRegistry cloneWithConfiguration(@Nullable SerdeConfiguration configuration,
                                                @Nullable SerializationConfiguration serializationConfiguration,
                                                @Nullable DeserializationConfiguration deserializationConfiguration,
                                                SerdeIntrospections introspections) {
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
        return beanContext().findBean(serializerClass).orElseThrow(() -> new SerdeException("Cannot find serializer: " + serializerClass));
    }

    @Override
    public <T, D extends Deserializer<? extends T>> D findCustomDeserializer(Class<? extends D> deserializerClass) throws SerdeException {
        checkBeanContext();
        return beanContext().findBean(deserializerClass).orElseThrow(() -> new SerdeException("Cannot find deserializer: " + deserializerClass));
    }

    @Override
    public <D extends PropertyNamingStrategy> D findNamingStrategy(Class<? extends D> namingStrategyClass) throws SerdeException {
        checkBeanContext();
        return beanContext().findBean(namingStrategyClass).orElseThrow(() -> new SerdeException("Cannot find naming strategy: " + namingStrategyClass));
    }

    private void checkBeanContext() throws SerdeException {
        if (beanContext == null) {
            throw new SerdeException("No bean context present!");
        }
    }

    private BeanContext beanContext() {
        return Objects.requireNonNull(beanContext);
    }

    @Override
    public <T> Deserializer<? extends T> findDeserializer(Argument<? extends T> type) throws SerdeException {
        Objects.requireNonNull(type, "Type cannot be null");
        final TypeKey key = new TypeKey(type);
        final Deserializer<?> deserializer = deserializerMap.get(key);
        if (deserializer != null) {
            return (Deserializer<? extends T>) deserializer;
        }
        if (type.getType().equals(Object.class)) {
            return (Deserializer<? extends T>) objectDeserializer;
        }
        if (type.getType().equals(Object[].class)) {
            return (Deserializer<? extends T>) objectArraySerde;
        }

        Collection<BeanDefinition<Deserializer>> beanDefinitions = MatchArgumentQualifier.covariant(Deserializer.class, type)
            .filter(Deserializer.class, deserializers);
        BeanDefinition<Deserializer> deserBeanDefinition;
        if (beanDefinitions.size() == 1) {
            deserBeanDefinition = beanDefinitions.iterator().next();
        } else if (!beanDefinitions.isEmpty()) {
            deserBeanDefinition = lastChanceResolveDeserializer(type, beanDefinitions);
        } else {
            deserBeanDefinition = null;
        }
        if (deserBeanDefinition != null) {
            Deserializer<?> deser;
            if (deserBeanDefinition instanceof InternalSerdeBeanDefinition<?> internalSerdeBeanDefinition) {
                deser = (Deserializer<?>) internalSerdeBeanDefinition.value;
            } else if (createSpecificDeserializerConstructor(deserBeanDefinition)) {
                deser = new SpecificBeanDeserializer(beanContext, deserBeanDefinition.getBeanType());
            } else {
                deser = beanContext.getBean(deserBeanDefinition);
            }
            deserializerMap.put(key, deser);
            return (Deserializer<? extends T>) deser;
        }
        if (key.getType().isArray()) {
            deserializerMap.put(key, objectArraySerde);
            return (Deserializer<? extends T>) objectArraySerde;
        }
        deserializerMap.put(key, objectDeserializer);
        return (Deserializer<? extends T>) objectDeserializer;
    }

    @Override
    public <T> Collection<BeanIntrospection<? extends T>> getDeserializableSubtypes(Class<T> superType) {
        return introspections.findSubtypeDeserializables(superType);
    }

    @Override
    public <T> Serializer<? super T> findSerializer(Argument<? extends T> type) throws SerdeException {
        Objects.requireNonNull(type, "Type cannot be null");
        final TypeKey key = new TypeKey(type);
        SerializerWrapper wrapper = serializerMap.get(key);
        if (wrapper != null) {
            return (Serializer<? super T>) wrapper.serializer;
        }
        if (type.getType().equals(Object.class)) {
            return objectSerializer;
        }
        if (type.getType().equals(Object[].class)) {
            return (Serializer<? super T>) objectArraySerde;
        }

        Collection<BeanDefinition<Serializer>> beanDefinitions = MatchArgumentQualifier.contravariant(Serializer.class, type)
            .filter(Serializer.class, serializers);
        BeanDefinition<Serializer> serializerBeanDefinition;
        if (beanDefinitions.size() == 1) {
            serializerBeanDefinition = beanDefinitions.iterator().next();
        } else if (!beanDefinitions.isEmpty()) {
            serializerBeanDefinition = lastChanceResolveSerializer(type, beanDefinitions);
        } else {
            serializerBeanDefinition = null;
        }
        if (serializerBeanDefinition != null) {
            Serializer<?> ser;
            if (serializerBeanDefinition instanceof InternalSerdeBeanDefinition<?> internalSerdeBeanDefinition) {
                ser = (Serializer<?>) internalSerdeBeanDefinition.value;
            } else if (createSpecificSerializerConstructor(serializerBeanDefinition)) {
                ser = new SpecificBeanSerializer(beanContext, serializerBeanDefinition.getBeanType());
            } else {
                ser = beanContext.getBean(serializerBeanDefinition);
            }
            serializerMap.put(key, new SerializerWrapper(ser));
            return (Serializer<? super T>) ser;
        }
        if (key.getType().isArray()) {
            serializerMap.put(key, new SerializerWrapper(objectArraySerde));
            return (Serializer<? super T>) objectArraySerde;
        }
        serializerMap.put(key, new SerializerWrapper(objectSerializer));
        return objectSerializer;
    }

    private boolean createSpecificSerializerConstructor(BeanDefinition<Serializer> serializerBeanDefinition) {
        return hasRuntimeConstructorArguments(serializerBeanDefinition, Serializer.EncoderContext.class);
    }

    private boolean createSpecificDeserializerConstructor(BeanDefinition<Deserializer> deserBeanDefinition) {
        return hasRuntimeConstructorArguments(deserBeanDefinition, Deserializer.DecoderContext.class);
    }

    private static boolean hasRuntimeConstructorArguments(BeanDefinition<?> beanDefinition, Class<?> contextType) {
        if (!(beanDefinition instanceof ParametrizedInstantiatableBeanDefinition<?> parametrizedBeanDefinition)) {
            return false;
        }
        Argument<?>[] arguments = parametrizedBeanDefinition.getRequiredArguments();
        return arguments.length == 2
            && arguments[0].getType().equals(contextType)
            && arguments[1].getType().equals(Argument.class);
    }

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

        // When multiple serializers match, prefer the one whose type argument most closely matches.
        // If the type has no type parameters (raw collection), prefer candidates with type-variable
        // type arguments (more generic) over candidates with concrete type arguments.
        // This prevents a user-defined Serializer<List<Foo>> from being used for raw/unparameterized
        // collection types like the result of List.of(...).
        // See https://github.com/micronaut-projects/micronaut-serialization/issues/1187
        if (type.getTypeParameters().length == 0) {
            List<BeanDefinition<Serializer>> genericCandidates = candidates.stream()
                .filter(candidate -> {
                    List<Argument<?>> typeArgs = candidate.getTypeArguments(Serializer.class);
                    if (typeArgs.isEmpty()) {
                        return false;
                    }
                    Argument<?> candidateArg = typeArgs.get(0);
                    // A candidate is "generic" if its type argument has no concrete type parameters
                    // (i.e. all type parameters are type variables or the candidate has no type params)
                    return hasOnlyTypeVariableParameters(candidateArg);
                })
                .toList();
            if (!genericCandidates.isEmpty() && genericCandidates.size() < candidates.size()) {
                candidates = genericCandidates;
            }
        }

        return lastChanceResolve(type, candidates, "serializers");
    }

    /**
     * Returns true if the given argument has only type-variable parameters (no concrete types in its type parameters).
     * This is used to detect "generic" serializers like {@code Serializer<List<E>>} vs specific ones like
     * {@code Serializer<List<String>>}.
     */
    private static boolean hasOnlyTypeVariableParameters(Argument<?> argument) {
        Argument<?>[] typeParameters = argument.getTypeParameters();
        if (typeParameters.length == 0) {
            return true;
        }
        for (Argument<?> typeParameter : typeParameters) {
            if (!typeParameter.isTypeVariable()) {
                return false;
            }
        }
        return true;
    }

    private BeanDefinition<Deserializer> lastChanceResolveDeserializer(
        Argument<?> type,
        Collection<BeanDefinition<Deserializer>> candidates) throws SerdeException {

        return lastChanceResolve(type, candidates, "deserializers");
    }

    @Override
    public Serializer.EncoderContext newEncoderContext(@Nullable Class<?> view) {
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
    public Deserializer.DecoderContext newDecoderContext(@Nullable Class<?> view) {
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

    private record SpecificBeanSerializer(BeanContext beanContext,
                                          Class<? extends Serializer> beanType)
        implements CustomizableSerializer<Object>, FormattedSerializer<Object> {

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public Serializer<Object> createSpecific(Serializer.EncoderContext context,
                                                 Argument<? extends Object> type) throws SerdeException {
            Serializer serializer = beanContext.createBean(beanType, context, type);
            return serializer.createSpecific(context, type);
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public Serializer<Object> createSpecific(Serializer.EncoderContext context,
                                                 Argument<? extends Object> type,
                                                 FormatConfiguration format) throws SerdeException {
            Serializer serializer = beanContext.createBean(beanType, context, type);
            if (serializer instanceof FormattedSerializer formattedSerializer) {
                return formattedSerializer.createSpecific(context, type, format);
            }
            return serializer.createSpecific(context, type);
        }
    }

    private record SpecificBeanDeserializer(BeanContext beanContext,
                                            Class<? extends Deserializer> beanType)
        implements CustomizableDeserializer<Object>, FormattedDeserializer<Object> {

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public Deserializer<Object> createSpecific(Deserializer.DecoderContext context,
                                                   Argument<? super Object> type) throws SerdeException {
            Deserializer deserializer = beanContext.createBean(beanType, context, type);
            return deserializer.createSpecific(context, type);
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public Deserializer<Object> createSpecific(Deserializer.DecoderContext context,
                                                   Argument<? super Object> type,
                                                   FormatConfiguration format) throws SerdeException {
            Deserializer deserializer = beanContext.createBean(beanType, context, type);
            if (deserializer instanceof FormattedDeserializer formattedDeserializer) {
                return formattedDeserializer.createSpecific(context, type, format);
            }
            return deserializer.createSpecific(context, type);
        }
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

        @Override
        public Argument<T> asArgument() {
            return (Argument<T>) argument;
        }

        @Override
        public List<Argument<?>> getTypeArguments() {
            return typeParameters;
        }

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
        public boolean isEnabled(BeanContext context, @Nullable BeanResolutionContext resolutionContext) {
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
