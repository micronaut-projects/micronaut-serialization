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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.serde.Deserializer;
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
import io.micronaut.serde.support.util.MatchArgumentQualifier;
import io.micronaut.serde.support.util.TypeKey;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of the {@link io.micronaut.serde.SerdeRegistry} interface.
 */
@Singleton
@BootstrapContextCompatible
public class DefaultSerdeRegistry implements SerdeRegistry {
    /**
     * @deprecated Internal serdes shouldn't be accessed as a static field
     */
    @Deprecated(since = "2.9.0")
    public static final Serde<Integer> INTEGER_SERDE = Serdes.INTEGER_SERDE;
    /**
     * @deprecated Internal serdes shouldn't be accessed as a static field
     */
    @Deprecated(since = "2.9.0")
    public static final Serde<Long> LONG_SERDE = Serdes.LONG_SERDE;
    /**
     * @deprecated Internal serdes shouldn't be accessed as a static field
     */
    @Deprecated(since = "2.9.0")
    public static final Serde<Short> SHORT_SERDE = Serdes.SHORT_SERDE;
    /**
     * @deprecated Internal serdes shouldn't be accessed as a static field
     */
    @Deprecated(since = "2.9.0")
    public static final Serde<Float> FLOAT_SERDE = Serdes.FLOAT_SERDE;
    /**
     * @deprecated Internal serdes shouldn't be accessed as a static field
     */
    @Deprecated(since = "2.9.0")
    public static final Serde<Byte> BYTE_SERDE = Serdes.BYTE_SERDE;
    /**
     * @deprecated Internal serdes shouldn't be accessed as a static field
     */
    @Deprecated(since = "2.9.0")
    public static final Serde<Double> DOUBLE_SERDE = Serdes.DOUBLE_SERDE;
    /**
     * @deprecated Internal serdes shouldn't be accessed as a static field
     */
    @Deprecated(since = "2.9.0")
    public static final Serde<OptionalInt> OPTIONAL_INT_SERDE = Serdes.OPTIONAL_INT_SERDE;
    /**
     * @deprecated Internal serdes shouldn't be accessed as a static field
     */
    @Deprecated(since = "2.9.0")
    public static final Serde<OptionalDouble> OPTIONAL_DOUBLE_SERDE = Serdes.OPTIONAL_DOUBLE_SERDE;
    /**
     * @deprecated Internal serdes shouldn't be accessed as a static field
     */
    @Deprecated(since = "2.9.0")
    public static final Serde<OptionalLong> OPTIONAL_LONG_SERDE = Serdes.OPTIONAL_LONG_SERDE;
    /**
     * @deprecated Internal serdes shouldn't be accessed as a static field
     */
    @Deprecated(since = "2.9.0")
    public static final Serde<BigDecimal> BIG_DECIMAL_SERDE = Serdes.BIG_DECIMAL_SERDE;
    /**
     * @deprecated Internal serdes shouldn't be accessed as a static field
     */
    @Deprecated(since = "2.9.0")
    public static final Serde<BigInteger> BIG_INTEGER_SERDE = Serdes.BIG_INTEGER_SERDE;
    /**
     * @deprecated Internal serdes shouldn't be accessed as a static field
     */
    @Deprecated(since = "2.9.0")
    public static final Serde<UUID> UUID_SERDE = Serdes.UUID_SERDE;
    /**
     * @deprecated Internal serdes shouldn't be accessed as a static field
     */
    @Deprecated(since = "2.9.0")
    public static final Serde<URL> URL_SERDE = Serdes.URL_SERDE;
    /**
     * @deprecated Internal serdes shouldn't be accessed as a static field
     */
    @Deprecated(since = "2.9.0")
    public static final Serde<URI> URI_SERDE = Serdes.URI_SERDE;
    /**
     * @deprecated Internal serdes shouldn't be accessed as a static field
     */
    @Deprecated(since = "2.9.0")
    public static final Serde<Charset> CHARSET_SERDE = Serdes.CHARSET_SERDE;
    /**
     * @deprecated Internal serdes shouldn't be accessed as a static field
     */
    @Deprecated(since = "2.9.0")
    public static final Serde<TimeZone> TIME_ZONE_SERDE = Serdes.TIME_ZONE_SERDE;
    /**
     * @deprecated Internal serdes shouldn't be accessed as a static field
     */
    @Deprecated(since = "2.9.0")
    public static final Serde<Locale> LOCALE_SERDE = Serdes.LOCALE_SERDE;
    /**
     * @deprecated Internal serdes shouldn't be accessed as a static field
     */
    @Deprecated(since = "2.9.0")
    public static final Serde<int[]> INT_ARRAY_SERDE = Serdes.INT_ARRAY_SERDE;
    /**
     * @deprecated Internal serdes shouldn't be accessed as a static field
     */
    @Deprecated(since = "2.9.0")
    public static final Serde<long[]> LONG_ARRAY_SERDE = Serdes.LONG_ARRAY_SERDE;
    /**
     * @deprecated Internal serdes shouldn't be accessed as a static field
     */
    @Deprecated(since = "2.9.0")
    public static final Serde<float[]> FLOAT_ARRAY_SERDE = Serdes.FLOAT_ARRAY_SERDE;
    /**
     * @deprecated Internal serdes shouldn't be accessed as a static field
     */
    @Deprecated(since = "2.9.0")
    public static final Serde<short[]> SHORT_ARRAY_SERDE = Serdes.SHORT_ARRAY_SERDE;
    /**
     * @deprecated Internal serdes shouldn't be accessed as a static field
     */
    @Deprecated(since = "2.9.0")
    public static final Serde<double[]> DOUBLE_ARRAY_SERDE = Serdes.DOUBLE_ARRAY_SERDE;
    /**
     * @deprecated Internal serdes shouldn't be accessed as a static field
     */
    @Deprecated(since = "2.9.0")
    public static final Serde<boolean[]> BOOLEAN_ARRAY_SERDE = Serdes.BOOLEAN_ARRAY_SERDE;
    /**
     * @deprecated Internal serdes shouldn't be accessed as a static field
     */
    @Deprecated(since = "2.9.0")
    public static final Serde<byte[]> BYTE_ARRAY_SERDE = Serdes.BYTE_ARRAY_SERDE;
    /**
     * @deprecated Internal serdes shouldn't be accessed as a static field
     */
    @Deprecated(since = "2.9.0")
    public static final Serde<char[]> CHAR_ARRAY_SERDE = Serdes.CHAR_ARRAY_SERDE;
    /**
     * @deprecated Internal serdes shouldn't be accessed as a static field
     */
    @Deprecated(since = "2.9.0")
    public static final Serde<String> STRING_SERDE = Serdes.STRING_SERDE;
    /**
     * @deprecated Internal serdes shouldn't be accessed as a static field
     */
    @Deprecated(since = "2.9.0")
    public static final Serde<Boolean> BOOLEAN_SERDE = Serdes.BOOLEAN_SERDE;
    /**
     * @deprecated Internal serdes shouldn't be accessed as a static field
     */
    @Deprecated(since = "2.9.0")
    public static final Serde<Charset> CHAR_SERDE = Serdes.CHARSET_SERDE;
    /**
     * @deprecated Internal serdes shouldn't be accessed as a static field
     */
    @Deprecated(since = "2.9.0")
    public static final List<SerdeRegistrar<?>> DEFAULT_SERDES = Serdes.LEGACY_DEFAULT_SERDES;

    private final List<BeanDefinition<Serializer>> serializers = new ArrayList<>(100);
    private final List<BeanDefinition<Deserializer>> deserializers = new ArrayList<>(100);
    private final List<BeanDefinition<Serde>> internalSerdes = new ArrayList<>(100);

    private final Map<TypeKey, Serializer<?>> serializerMap = new ConcurrentHashMap<>(50);
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
     * @param objectSerializer             The object serializer
     * @param objectDeserializer           The object deserializer
     * @param objectArraySerde             The object array Serde
     * @param introspections               The introspections
     * @param conversionService            The conversion service
     * @param serdeConfiguration           The {@link SerdeConfiguration}
     * @param serializationConfiguration   The {@link SerializationConfiguration}
     * @param deserializationConfiguration The {@link DeserializationConfiguration}
     * @deprecated Use {@link #DefaultSerdeRegistry(BeanContext, SerdeIntrospections, ConversionService, SerdeConfiguration, SerializationConfiguration, DeserializationConfiguration)}
     */
    @Deprecated(forRemoval = true, since = "2.9.0")
    public DefaultSerdeRegistry(
        BeanContext beanContext,
        ObjectSerializer objectSerializer,
        ObjectDeserializer objectDeserializer,
        Serde<Object[]> objectArraySerde,
        SerdeIntrospections introspections,
        ConversionService conversionService,
        SerdeConfiguration serdeConfiguration,
        SerializationConfiguration serializationConfiguration,
        DeserializationConfiguration deserializationConfiguration) {
        this.serdeConfiguration = serdeConfiguration;
        this.serializationConfiguration = serializationConfiguration;
        this.deserializationConfiguration = deserializationConfiguration;
        this.introspections = introspections;
        this.objectArraySerde = objectArraySerde;
        this.beanContext = beanContext;
        this.conversionService = conversionService;

        registerSerializersDeserializersFromBeanContext(beanContext);
        registerBuiltInSerdes();

        this.objectSerializer = objectSerializer;
        this.objectDeserializer = objectDeserializer;
    }

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
    @Inject
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

    /**
     * @param beanContext        The bean context
     * @param objectSerializer   The object serializer
     * @param objectDeserializer The object deserializer
     * @param objectArraySerde   The object array Serde
     * @param introspections     The introspections
     * @param conversionService  The conversion service
     * @deprecated Use {@link #DefaultSerdeRegistry(BeanContext, ObjectSerializer, ObjectDeserializer, Serde, SerdeIntrospections, ConversionService, SerdeConfiguration, SerializationConfiguration, DeserializationConfiguration)} instead
     */
    @Deprecated
    public DefaultSerdeRegistry(
        BeanContext beanContext,
        ObjectSerializer objectSerializer,
        ObjectDeserializer objectDeserializer,
        Serde<Object[]> objectArraySerde,
        SerdeIntrospections introspections,
        ConversionService conversionService) {
        this(beanContext, objectSerializer, objectDeserializer, objectArraySerde, introspections, conversionService, beanContext.getBean(SerdeConfiguration.class), beanContext.getBean(SerializationConfiguration.class), beanContext.getBean(DeserializationConfiguration.class));
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
                && internalSerdeBeanDefinition.typeArgument.equalsType(type)) {
                return (Serde<T>) internalSerdeBeanDefinition.value;
            }
        }
        return null;
    }

    private void registerBuiltInSerdes() {
        Serdes.register(serdeConfiguration, introspections, serdeRegistrar -> {
            for (Argument<?> type : serdeRegistrar.getTypes()) {
                deserializers.add(new InternalSerdeBeanDefinition<>(type, Deserializer.class, serdeRegistrar, serdeRegistrar.getOrder()));
                serializers.add(new InternalSerdeBeanDefinition<>(type, Serializer.class, serdeRegistrar, serdeRegistrar.getOrder()));
                internalSerdes.add(new InternalSerdeBeanDefinition<>(type, Serde.class, serdeRegistrar, serdeRegistrar.getOrder()));
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

        Collection<BeanDefinition<Deserializer>> beanDefinitions = MatchArgumentQualifier.ofSuperVariable(Deserializer.class, type)
            .filter(Deserializer.class, deserializers);
        Deserializer<?> deser = null;
        if (beanDefinitions.size() == 1) {
            deser = getBean(beanDefinitions.iterator().next());
        } else if (!beanDefinitions.isEmpty()) {
            deser = getBean(lastChanceResolveDeserializer(type, beanDefinitions));
        }
        if (deser != null) {
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
        final Serializer<?> serializer = serializerMap.get(key);
        if (serializer != null) {
            return (Serializer<? super T>) serializer;
        }
        if (type.getType().equals(Object.class)) {
            return objectSerializer;
        }
        if (type.getType().equals(Object[].class)) {
            return (Serializer<? super T>) objectArraySerde;
        }

        Collection<BeanDefinition<Serializer>> beanDefinitions = MatchArgumentQualifier.ofExtendsVariable(Serializer.class, type)
            .filter(Serializer.class, serializers);
        Serializer<?> ser = null;
        if (beanDefinitions.size() == 1) {
            ser = getBean(beanDefinitions.iterator().next());
        } else if (!beanDefinitions.isEmpty()) {
            BeanDefinition<Serializer> definition = lastChanceResolveSerializer(type, beanDefinitions);
            ser = getBean(definition);
        }
        if (ser != null) {
            serializerMap.put(key, ser);
            return (Serializer<? super T>) ser;
        }
        if (key.getType().isArray()) {
            serializerMap.put(key, objectArraySerde);
            return (Serializer<? super T>) objectArraySerde;
        }
        serializerMap.put(key, objectSerializer);
        return objectSerializer;
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
        if (view != null) {
            return new DefaultEncoderContext(this) {
                @Override
                public boolean hasView(Class<?>... views) {
                    if (view == Object.class) {
                        return true;
                    }
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
        if (view != null) {
            return new DefaultDecoderContext(this) {
                @Override
                public boolean hasView(Class<?>... views) {
                    if (view == Object.class) {
                        return true;
                    }
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

}
