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
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serde;
import io.micronaut.serde.SerdeIntrospections;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.deserializers.ObjectDeserializer;
import io.micronaut.serde.support.serdes.NumberSerde;
import io.micronaut.serde.support.serializers.ObjectSerializer;
import io.micronaut.serde.support.util.TypeKey;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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
import java.util.stream.Collectors;

/**
 * Default implementation of the {@link io.micronaut.serde.SerdeRegistry} interface.
 */
@Singleton
@BootstrapContextCompatible
public class DefaultSerdeRegistry implements SerdeRegistry {

    public static final IntegerSerde INTEGER_SERDE = new IntegerSerde();
    public static final LongSerde LONG_SERDE = new LongSerde();
    public static final ShortSerde SHORT_SERDE = new ShortSerde();
    public static final FloatSerde FLOAT_SERDE = new FloatSerde();
    public static final ByteSerde BYTE_SERDE = new ByteSerde();
    public static final DoubleSerde DOUBLE_SERDE = new DoubleSerde();
    public static final OptionalIntSerde OPTIONAL_INT_SERDE = new OptionalIntSerde();
    public static final OptionalDoubleSerde OPTIONAL_DOUBLE_SERDE = new OptionalDoubleSerde();
    public static final OptionalLongSerde OPTIONAL_LONG_SERDE = new OptionalLongSerde();
    public static final BigDecimalSerde BIG_DECIMAL_SERDE = new BigDecimalSerde();
    public static final BigIntegerSerde BIG_INTEGER_SERDE = new BigIntegerSerde();
    public static final UUIDSerde UUID_SERDE = new UUIDSerde();
    public static final URLSerde URL_SERDE = new URLSerde();
    public static final URISerde URI_SERDE = new URISerde();
    public static final CharsetSerde CHARSET_SERDE = new CharsetSerde();
    public static final TimeZoneSerde TIME_ZONE_SERDE = new TimeZoneSerde();
    public static final LocaleSerde LOCALE_SERDE = new LocaleSerde();
    public static final IntArraySerde INT_ARRAY_SERDE = new IntArraySerde();
    public static final LongArraySerde LONG_ARRAY_SERDE = new LongArraySerde();
    public static final FloatArraySerde FLOAT_ARRAY_SERDE = new FloatArraySerde();
    public static final ShortArraySerde SHORT_ARRAY_SERDE = new ShortArraySerde();
    public static final DoubleArraySerde DOUBLE_ARRAY_SERDE = new DoubleArraySerde();
    public static final BooleanArraySerde BOOLEAN_ARRAY_SERDE = new BooleanArraySerde();
    public static final ByteArraySerde BYTE_ARRAY_SERDE = new ByteArraySerde();
    public static final CharArraySerde CHAR_ARRAY_SERDE = new CharArraySerde();

    public static final StringSerde STRING_SERDE = new StringSerde();

    public static final BooleanSerde BOOLEAN_SERDE = new BooleanSerde();
    public static final CharSerde CHAR_SERDE = new CharSerde();
    public static final List<SerdeRegistrar<?>> DEFAULT_SERDES = List.of(
        BOOLEAN_SERDE,
        BYTE_SERDE,
        CHAR_SERDE,
        DOUBLE_SERDE,
        FLOAT_SERDE,
        INTEGER_SERDE,
        LONG_SERDE,
        SHORT_SERDE,
        STRING_SERDE,
        OPTIONAL_INT_SERDE,
        OPTIONAL_DOUBLE_SERDE,
        OPTIONAL_LONG_SERDE,
        BIG_DECIMAL_SERDE,
        BIG_INTEGER_SERDE,
        UUID_SERDE,
        URL_SERDE,
        URI_SERDE,
        CHARSET_SERDE,
        TIME_ZONE_SERDE,
        LOCALE_SERDE,
        INT_ARRAY_SERDE,
        LONG_ARRAY_SERDE,
        FLOAT_ARRAY_SERDE,
        SHORT_ARRAY_SERDE,
        DOUBLE_ARRAY_SERDE,
        BOOLEAN_ARRAY_SERDE,
        BYTE_ARRAY_SERDE,
        CHAR_ARRAY_SERDE
    );
    private final Serializer<Object> objectSerializer;
    private final Map<Class<?>, List<BeanDefinition<Serializer>>> serializerDefMap;
    private final Map<Class<?>, List<BeanDefinition<Deserializer>>> deserializerDefMap;
    private final Map<TypeKey, Serializer<?>> serializerMap = new ConcurrentHashMap<>(50);
    private final Map<TypeKey, Deserializer<?>> deserializerMap = new ConcurrentHashMap<>(50);
    private final BeanContext beanContext;
    private final SerdeIntrospections introspections;
    private final Deserializer<Object> objectDeserializer;
    private final Serde<Object[]> objectArraySerde;
    private final ConversionService conversionService;

    /**
     * Default constructor.
     * @param beanContext The bean context
     * @param objectSerializer  The object serializer
     * @param objectDeserializer The object deserializer
     * @param objectArraySerde The object array Serde
     * @param introspections The introspections
     * @param conversionService The conversion service
     */
    public DefaultSerdeRegistry(
        BeanContext beanContext,
        ObjectSerializer objectSerializer,
        ObjectDeserializer objectDeserializer,
        Serde<Object[]> objectArraySerde,
        SerdeIntrospections introspections,
        ConversionService conversionService) {
        final Collection<BeanDefinition<Serializer>> serializers =
                beanContext.getBeanDefinitions(Serializer.class);
        final Collection<BeanDefinition<Deserializer>> deserializers =
                beanContext.getBeanDefinitions(Deserializer.class);
        this.introspections = introspections;
        this.serializerDefMap = new HashMap<>(serializers.size() + 30); // some padding
        this.deserializerDefMap = new HashMap<>(deserializers.size() + 30); // some padding
        this.objectArraySerde = objectArraySerde;
        this.beanContext = beanContext;
        for (BeanDefinition<Serializer> serializer : serializers) {
            final List<Argument<?>> typeArguments = serializer.getTypeArguments(Serializer.class);
            if (CollectionUtils.isNotEmpty(typeArguments)) {
                final Argument<?> argument = typeArguments.iterator().next();
                if (!argument.equalsType(Argument.OBJECT_ARGUMENT)) {
                    final Class<?> t = argument.getType();
                    serializerDefMap
                            .computeIfAbsent(t, aClass -> new ArrayList<>(5))
                            .add(serializer);
                    final Class<?> primitiveType = ReflectionUtils.getPrimitiveType(t);
                    if (primitiveType != t) {
                        serializerDefMap
                                .computeIfAbsent(primitiveType, aClass -> new ArrayList<>(5))
                                .add(serializer);
                    }
                }
            } else {
                throw new ConfigurationException("Serializer without generic types defined: " + serializer.getBeanType());
            }
        }
        for (BeanDefinition<Deserializer> deserializer : deserializers) {
            final List<Argument<?>> typeArguments = deserializer.getTypeArguments(Deserializer.class);
            if (CollectionUtils.isNotEmpty(typeArguments)) {
                final Argument<?> argument = typeArguments.iterator().next();
                if (!argument.equalsType(Argument.OBJECT_ARGUMENT)) {
                    final Class<?> t = argument.getType();
                    deserializerDefMap
                            .computeIfAbsent(t, aClass -> new ArrayList<>(5))
                            .add(deserializer);
                    final Class<?> primitiveType = ReflectionUtils.getPrimitiveType(t);
                    if (primitiveType != t) {
                        deserializerDefMap
                                .computeIfAbsent(primitiveType, aClass -> new ArrayList<>(5))
                                .add(deserializer);
                    }
                }
            } else {
                throw new ConfigurationException("Deserializer without generic types defined: " + deserializer.getBeanType());
            }
        }

        registerBuiltInSerdes();
        this.objectSerializer = objectSerializer;
        this.objectDeserializer = objectDeserializer;
        this.conversionService = conversionService;
    }

    private void registerBuiltInSerdes() {
        DEFAULT_SERDES.forEach(this::register);
    }

    private void register(SerdeRegistrar<?> serdeRegistrar) {
        for (Argument<?> type : serdeRegistrar.getTypes()) {
            final TypeKey typeEntry = new TypeKey(type);
            // if it hasn't been overridden by a bean
            if (!deserializerDefMap.containsKey(type.getType())) {
                DefaultSerdeRegistry.this.deserializerMap.put(typeEntry, serdeRegistrar);
            }
            if (!serializerDefMap.containsKey(type.getType())) {
                DefaultSerdeRegistry.this.serializerMap.put(typeEntry, serdeRegistrar);
            }
        }
    }

    @Override
    public <T, D extends Serializer<? extends T>> D findCustomSerializer(Class<? extends D> serializerClass) throws SerdeException {
        return beanContext.findBean(serializerClass).orElseThrow(() -> new SerdeException("Cannot find serializer: " + serializerClass));
    }

    @Override
    public <T, D extends Deserializer<? extends T>> D findCustomDeserializer(Class<? extends D> deserializerClass) throws SerdeException {
        return beanContext.findBean(deserializerClass).orElseThrow(() -> new SerdeException("Cannot find deserializer: " + deserializerClass));
    }

    @Override
    public <D extends PropertyNamingStrategy> D findNamingStrategy(Class<? extends D> namingStrategyClass) throws SerdeException {
        return beanContext.findBean(namingStrategyClass).orElseThrow(() -> new SerdeException("Cannot find naming strategy: " + namingStrategyClass));
    }

    @Override
    public <T> Deserializer<? extends T> findDeserializer(Argument<? extends T> type) {
        Objects.requireNonNull(type, "Type cannot be null");
        final TypeKey key = new TypeKey(type);
        final Deserializer<?> deserializer = deserializerMap.get(key);
        if (deserializer != null) {
            return (Deserializer<? extends T>) deserializer;
        } else {
            final Argument<Deserializer> deserializerArgument = Argument.of(Deserializer.class, type);
            final Collection<BeanRegistration<Deserializer>> beanRegistrations = beanContext
                    .getBeanRegistrations(deserializerArgument, null);
            Deserializer<?> deser = null;
            if (beanRegistrations.size() == 1) {
                deser = beanRegistrations.iterator().next().bean();
            } else if (!beanRegistrations.isEmpty()) {
                final List<BeanRegistration<Deserializer>> results = beanRegistrations.stream()
                        .filter((r) -> {
                            final Class<?>[] typeParameters = r.getBeanDefinition().getTypeParameters(Deserializer.class);
                            return typeParameters.length == 1 && typeParameters[0].equals(type.getType());
                        })
                        .collect(Collectors.toList());
                if (results.size() == 1) {
                    deser = results.iterator().next().bean();
                } else {
                    deser = beanContext.findBean(deserializerArgument).orElse(null);
                }
            }
            if (deser != null) {
                deserializerMap.put(key, deser);
                return (Deserializer<? extends T>) deser;
            }
        }
        if (key.getType().isArray()) {
            deserializerMap.put(key, objectArraySerde);
            return (Deserializer<? extends T>) objectArraySerde;
        } else {
            deserializerMap.put(key, objectDeserializer);
            return (Deserializer<? extends T>) objectDeserializer;
        }
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
            //noinspection unchecked
            return (Serializer<? super T>) serializer;
        } else {
            List<BeanDefinition<Serializer>> possibles = serializerDefMap.get(type.getType());
            if (possibles == null) {
                for (Map.Entry<Class<?>, List<BeanDefinition<Serializer>>> entry : serializerDefMap.entrySet()) {
                    final Class<?> targetType = entry.getKey();
                    if (targetType.isAssignableFrom(type.getType())) {
                        possibles = entry.getValue();
                        final Argument<?>[] params = type.getTypeParameters();
                        if (ArrayUtils.isNotEmpty(params)) {
                            // narrow for generics
                            possibles = new ArrayList<>(possibles);
                            final Iterator<BeanDefinition<Serializer>> i = possibles.iterator();
                            while (i.hasNext()) {
                                final BeanDefinition<Serializer> bd = i.next();
                                final Argument<?>[] candidateParams = bd.getTypeArguments(Serializer.class).get(0)
                                        .getTypeParameters();
                                if (candidateParams.length == params.length) {
                                    for (int j = 0; j < params.length; j++) {
                                        Argument<?> param = params[j];
                                        final Argument<?> candidateParam = candidateParams[j];
                                        if (!(
                                                (param.getType() == candidateParam.getType()) ||
                                                        (
                                                                candidateParam.isTypeVariable() && candidateParam.getType()
                                                                        .isAssignableFrom(param.getType())))) {
                                            i.remove();
                                        }
                                    }
                                } else {
                                    i.remove();
                                }
                            }
                        }
                        break;
                    }
                }
            }
            if (possibles != null) {
                if (possibles.size() == 1) {
                    final BeanDefinition<Serializer> definition = possibles.iterator().next();
                    final Serializer locatedSerializer = beanContext.getBean(definition);
                    serializerMap.put(key, locatedSerializer);
                    return locatedSerializer;
                } else if (possibles.isEmpty()) {
                    throw new SerdeException("No serializers found for type: " + type);
                } else {

                    final BeanDefinition<Serializer> definition = lastChanceResolve(type, possibles);
                    final Serializer locatedSerializer = beanContext.getBean(definition);
                    serializerMap.put(key, locatedSerializer);
                    return locatedSerializer;
                }
            } else {
                serializerMap.put(key, objectSerializer);
            }
        }
        return objectSerializer;
    }

    private BeanDefinition<Serializer> lastChanceResolve(
            Argument<?> type,
            Collection<BeanDefinition<Serializer>> candidates) throws SerdeException {

        if (candidates.size() > 1) {
            List<BeanDefinition<Serializer>> primary = candidates.stream()
                    .filter(BeanDefinition::isPrimary)
                    .collect(Collectors.toList());
            if (!primary.isEmpty()) {
                candidates = primary;
            }
        }
        if (candidates.size() == 1) {
            return candidates.iterator().next();
        } else {
            candidates = candidates.stream().filter(candidate -> !candidate.hasDeclaredStereotype(Secondary.class)).collect(Collectors.toList());
            if (candidates.size() == 1) {
                return candidates.iterator().next();
            } else {
                if (candidates.stream().anyMatch(candidate -> candidate.hasAnnotation(Order.class))) {
                    // pick the bean with the highest priority
                    final Iterator<BeanDefinition<Serializer>> i = candidates.stream()
                            .sorted((bean1, bean2) -> {
                                int order1 = OrderUtil.getOrder(bean1.getAnnotationMetadata());
                                int order2 = OrderUtil.getOrder(bean2.getAnnotationMetadata());
                                return Integer.compare(order1, order2);
                            })
                            .iterator();
                    if (i.hasNext()) {
                        final BeanDefinition<Serializer> bean = i.next();
                        if (i.hasNext()) {
                            // check there are not 2 beans with the same order
                            final BeanDefinition<Serializer> next = i.next();
                            if (OrderUtil.getOrder(bean.getAnnotationMetadata()) == OrderUtil.getOrder(next.getAnnotationMetadata())) {
                                throw new SerdeException("Multiple possible serializers found for type [" + type + "]: " + candidates);
                            }
                        }

                        return bean;
                    } else {
                        throw new SerdeException("Multiple possible serializers found for type [" + type + "]: " + candidates);
                    }
                } else {
                    throw new SerdeException("Multiple possible serializers found for type [" + type + "]: " + candidates);
                }
            }
        }
    }

    @Override
    public Serializer.EncoderContext newEncoderContext(Class<?> view) {
        if (view != null) {
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
        if (view != null) {
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
        return conversionService;
    }

    private static final class ByteSerde extends SerdeRegistrar<Byte> implements NumberSerde<Byte> {
        @Override
        public Byte deserialize(Decoder decoder,
                                       DecoderContext decoderContext,
                                       Argument<? super Byte> type) throws IOException {
            return decoder.decodeByte();
        }

        @Override
        public void serialize(Encoder encoder,
                              EncoderContext context,
                              Argument<? extends Byte> type, Byte value) throws IOException {
            encoder.encodeByte(value);
        }

        @Override
        Argument<Byte> getType() {
            return Argument.of(Byte.class);
        }

        @Override
        protected Iterable<Argument<?>> getTypes() {
            return Arrays.asList(
                    getType(), Argument.BYTE
            );
        }

        @Nullable
        @Override
        public Byte getDefaultValue(@NonNull DecoderContext context, @NonNull Argument<? super Byte> type) {
            return type.isPrimitive() ? (byte) 0 : null;
        }
    }

    private static final class DoubleSerde extends SerdeRegistrar<Double> implements NumberSerde<Double> {
        @Override
        public Double deserialize(Decoder decoder,
                                        DecoderContext decoderContext,
                                        Argument<? super Double> type) throws IOException {
            return decoder.decodeDouble();
        }

        @Override
        public Double deserializeNullable(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super Double> type) throws IOException {
            return decoder.decodeDoubleNullable();
        }

        @Override
        public void serialize(Encoder encoder,
                              EncoderContext context,
                              Argument<? extends Double> type, Double value) throws IOException {
            encoder.encodeDouble(value);
        }

        @Override
        Argument<Double> getType() {
            return Argument.of(Double.class);
        }

        @Override
        protected Iterable<Argument<?>> getTypes() {
            return Arrays.asList(
                    getType(), Argument.DOUBLE
            );
        }

        @Nullable
        @Override
        public Double getDefaultValue(@NonNull DecoderContext context, @NonNull Argument<? super Double> type) {
            return type.isPrimitive() ? 0D : null;
        }
    }

    private static final class ShortSerde extends SerdeRegistrar<Short> implements NumberSerde<Short> {
        @Override
        public Short deserialize(Decoder decoder,
                                          DecoderContext decoderContext,
                                          Argument<? super Short> type) throws IOException {
            return decoder.decodeShort();
        }

        @Override
        public void serialize(Encoder encoder,
                              EncoderContext context,
                              Argument<? extends Short> type, Short value) throws IOException {
            encoder.encodeShort(value);
        }

        @Override
        Argument<Short> getType() {
            return Argument.of(Short.class);
        }

        @Override
        protected Iterable<Argument<?>> getTypes() {
            return Arrays.asList(
                    getType(), Argument.SHORT
            );
        }

        @Nullable
        @Override
        public Short getDefaultValue(@NonNull DecoderContext context, @NonNull Argument<? super Short> type) {
            return type.isPrimitive() ? (short) 0 : null;
        }
    }

    private static final class FloatSerde extends SerdeRegistrar<Float> implements NumberSerde<Float> {
        @Override
        public Float deserialize(Decoder decoder,
                                        DecoderContext decoderContext,
                                        Argument<? super Float> type) throws IOException {
            return decoder.decodeFloat();
        }

        @Override
        public void serialize(Encoder encoder,
                              EncoderContext context,
                              Argument<? extends Float> type, Float value) throws IOException {
            encoder.encodeFloat(value);
        }

        @Override
        Argument<Float> getType() {
            return Argument.of(Float.class);
        }

        @Override
        protected Iterable<Argument<?>> getTypes() {
            return Arrays.asList(
                    getType(), Argument.FLOAT
            );
        }

        @Nullable
        @Override
        public Float getDefaultValue(@NonNull DecoderContext context, @NonNull Argument<? super Float> type) {
            return type.isPrimitive() ? 0F : null;
        }
    }

    private static final class IntegerSerde extends SerdeRegistrar<Integer> implements NumberSerde<Integer> {
        @Override
        public Integer deserialize(Decoder decoder,
                                          DecoderContext decoderContext,
                                          Argument<? super Integer> type) throws IOException {
            return decoder.decodeInt();
        }

        @Override
        public Integer deserializeNullable(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super Integer> type) throws IOException {
            return decoder.decodeIntNullable();
        }

        @Override
        public void serialize(Encoder encoder,
                              EncoderContext context,
                              Argument<? extends Integer> type, Integer value) throws IOException {
            encoder.encodeInt(value);
        }

        @Override
        Argument<Integer> getType() {
            return Argument.of(Integer.class);
        }

        @Override
        protected Iterable<Argument<?>> getTypes() {
            return Arrays.asList(
                    getType(), Argument.INT
            );
        }

        @Nullable
        @Override
        public Integer getDefaultValue(@NonNull DecoderContext context, @NonNull Argument<? super Integer> type) {
            return type.isPrimitive() ? 0 : null;
        }
    }

    private static final class CharSerde extends SerdeRegistrar<Character> {
        @Override
        public Character deserialize(Decoder decoder,
                                          DecoderContext decoderContext,
                                          Argument<? super Character> type) throws IOException {
            return decoder.decodeChar();
        }

        @Override
        public void serialize(Encoder encoder,
                              EncoderContext context,
                              Argument<? extends Character> type, Character value) throws IOException {
            encoder.encodeChar(value);
        }

        @Override
        Argument<Character> getType() {
            return Argument.of(Character.class);
        }

        @Override
        protected Iterable<Argument<?>> getTypes() {
            return Arrays.asList(
                getType(), Argument.CHAR
            );
        }

        @Nullable
        @Override
        public Character getDefaultValue(@NonNull DecoderContext context, @NonNull Argument<? super Character> type) {
            return type.isPrimitive() ? (char) 0 : null;
        }
    }

    private static final class BooleanSerde extends SerdeRegistrar<Boolean> {
        @Override
        public Boolean deserialize(Decoder decoder,
                                          DecoderContext decoderContext,
                                          Argument<? super Boolean> type) throws IOException {
            return decoder.decodeBoolean();
        }

        @Override
        public Boolean deserializeNullable(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super Boolean> type) throws IOException {
            return decoder.decodeBooleanNullable();
        }

        @Override
        public void serialize(Encoder encoder,
                              EncoderContext context,
                              Argument<? extends Boolean> type, Boolean value) throws IOException {
            encoder.encodeBoolean(value);
        }

        @Override
        Argument<Boolean> getType() {
            return Argument.of(Boolean.class);
        }

        @Override
        protected Iterable<Argument<?>> getTypes() {
            return Arrays.asList(
                getType(), Argument.BOOLEAN
            );
        }

        @Nullable
        @Override
        public Boolean getDefaultValue(@NonNull DecoderContext context, @NonNull Argument<? super Boolean> type) {
            return type.isPrimitive() ? false : null;
        }
    }

    private static final class LongSerde extends SerdeRegistrar<Long> implements NumberSerde<Long> {
        @Override
        public Long deserialize(Decoder decoder,
                                          DecoderContext decoderContext,
                                          Argument<? super Long> type) throws IOException {
            return decoder.decodeLong();
        }

        @Override
        public void serialize(Encoder encoder,
                              EncoderContext context,
                              Argument<? extends Long> type, Long value) throws IOException {
            encoder.encodeLong(value);
        }

        @Override
        Argument<Long> getType() {
            return Argument.of(Long.class);
        }

        @Override
        protected Iterable<Argument<?>> getTypes() {
            return Arrays.asList(
                    getType(), Argument.LONG
            );
        }

        @Nullable
        @Override
        public Long getDefaultValue(@NonNull DecoderContext context, @NonNull Argument<? super Long> type) {
            return type.isPrimitive() ? 0L : null;
        }
    }

    private abstract static class SerdeRegistrar<T> implements Serde<T> {
        @NonNull
        abstract Argument<T> getType();

        @NonNull
        Iterable<Argument<?>> getTypes() {
            return Collections.singleton(getType());
        }

    }

    private static final class BooleanArraySerde extends SerdeRegistrar<boolean[]> {
        @Override
        public boolean[] deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super boolean[]> type)
                throws IOException {
            final Decoder arrayDecoder = decoder.decodeArray();
            boolean[] buffer = new boolean[50];
            int index = 0;
            while (arrayDecoder.hasNextArrayValue()) {
                if (buffer.length == index) {
                    buffer = Arrays.copyOf(buffer, buffer.length * 2);
                }
                if (!arrayDecoder.decodeNull()) {
                    buffer[index] = arrayDecoder.decodeBoolean();
                }
                index++;
            }
            arrayDecoder.finishStructure();
            return Arrays.copyOf(buffer, index);
        }

        @Override
        public void serialize(Encoder encoder,
                              EncoderContext context,
                              Argument<? extends boolean[]> type, boolean[] value) throws IOException {
            final Encoder arrayEncoder = encoder.encodeArray(type);
            for (boolean i : value) {
                arrayEncoder.encodeBoolean(i);
            }
            arrayEncoder.finishStructure();
        }

        @Override
        public boolean isEmpty(EncoderContext context, boolean[] value) {
            return value == null || value.length == 0;
        }

        @Override
        Argument<boolean[]> getType() {
            return Argument.of(boolean[].class);
        }
    }

    private static final class DoubleArraySerde extends SerdeRegistrar<double[]> {
        @Override
        public double[] deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super double[]> type)
                throws IOException {
            final Decoder arrayDecoder = decoder.decodeArray();
            double[] buffer = new double[50];
            int index = 0;
            while (arrayDecoder.hasNextArrayValue()) {
                if (buffer.length == index) {
                    buffer = Arrays.copyOf(buffer, buffer.length * 2);
                }
                if (!arrayDecoder.decodeNull()) {
                    buffer[index] = arrayDecoder.decodeDouble();
                }
                index++;
            }
            arrayDecoder.finishStructure();
            return Arrays.copyOf(buffer, index);
        }

        @Override
        public void serialize(Encoder encoder,
                              EncoderContext context,
                              Argument<? extends double[]> type, double[] value) throws IOException {
            final Encoder arrayEncoder = encoder.encodeArray(type);
            for (double i : value) {
                arrayEncoder.encodeDouble(i);
            }
            arrayEncoder.finishStructure();
        }

        @Override
        public boolean isEmpty(EncoderContext context, double[] value) {
            return value == null || value.length == 0;
        }

        @Override
        Argument<double[]> getType() {
            return Argument.of(double[].class);
        }
    }

    private static final class ShortArraySerde extends SerdeRegistrar<short[]> {
        @Override
        public short[] deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super short[]> type)
                throws IOException {
            final Decoder arrayDecoder = decoder.decodeArray();
            short[] buffer = new short[50];
            int index = 0;
            while (arrayDecoder.hasNextArrayValue()) {
                if (buffer.length == index) {
                    buffer = Arrays.copyOf(buffer, buffer.length * 2);
                }
                if (!arrayDecoder.decodeNull()) {
                    buffer[index] = arrayDecoder.decodeShort();
                }
                index++;
            }
            arrayDecoder.finishStructure();
            return Arrays.copyOf(buffer, index);
        }

        @Override
        public void serialize(Encoder encoder,
                              EncoderContext context,
                              Argument<? extends short[]> type, short[] value) throws IOException {
            final Encoder arrayEncoder = encoder.encodeArray(type);
            for (short i : value) {
                arrayEncoder.encodeShort(i);
            }
            arrayEncoder.finishStructure();
        }

        @Override
        public boolean isEmpty(EncoderContext context, short[] value) {
            return value == null || value.length == 0;
        }

        @Override
        Argument<short[]> getType() {
            return Argument.of(short[].class);
        }
    }

    private static final class FloatArraySerde extends SerdeRegistrar<float[]> {
        @Override
        public float[] deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super float[]> type)
                throws IOException {
            final Decoder arrayDecoder = decoder.decodeArray();
            float[] buffer = new float[50];
            int index = 0;
            while (arrayDecoder.hasNextArrayValue()) {
                if (buffer.length == index) {
                    buffer = Arrays.copyOf(buffer, buffer.length * 2);
                }
                if (!arrayDecoder.decodeNull()) {
                    buffer[index] = arrayDecoder.decodeFloat();
                }
                index++;
            }
            arrayDecoder.finishStructure();
            return Arrays.copyOf(buffer, index);
        }

        @Override
        public void serialize(Encoder encoder,
                              EncoderContext context,
                              Argument<? extends float[]> type, float[] value) throws IOException {
            final Encoder arrayEncoder = encoder.encodeArray(type);
            for (float i : value) {
                arrayEncoder.encodeFloat(i);
            }
            arrayEncoder.finishStructure();
        }

        @Override
        public boolean isEmpty(EncoderContext context, float[] value) {
            return value == null || value.length == 0;
        }

        @Override
        Argument<float[]> getType() {
            return Argument.of(float[].class);
        }
    }

    private static final class LongArraySerde extends SerdeRegistrar<long[]> {

        @Override
        public long[] deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super long[]> type)
                throws IOException {
            final Decoder arrayDecoder = decoder.decodeArray();
            long[] buffer = new long[50];
            int index = 0;
            while (arrayDecoder.hasNextArrayValue()) {
                if (buffer.length == index) {
                    buffer = Arrays.copyOf(buffer, buffer.length * 2);
                }
                if (!arrayDecoder.decodeNull()) {
                    buffer[index] = arrayDecoder.decodeLong();
                }
                index++;
            }
            arrayDecoder.finishStructure();
            return Arrays.copyOf(buffer, index);
        }

        @Override
        public void serialize(Encoder encoder,
                              EncoderContext context,
                              Argument<? extends long[]> type, long[] value) throws IOException {
            final Encoder arrayEncoder = encoder.encodeArray(type);
            for (long i : value) {
                arrayEncoder.encodeLong(i);
            }
            arrayEncoder.finishStructure();
        }

        @Override
        public boolean isEmpty(EncoderContext context, long[] value) {
            return value == null || value.length == 0;
        }

        @Override
        Argument<long[]> getType() {
            return Argument.of(long[].class);
        }
    }

    private static final class CharArraySerde extends SerdeRegistrar<char[]> {
        @Override
        public char[] deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super char[]> type)
                throws IOException {
            final Decoder arrayDecoder = decoder.decodeArray();
            char[] buffer = new char[100];
            int index = 0;
            while (arrayDecoder.hasNextArrayValue()) {
                if (buffer.length == index) {
                    buffer = Arrays.copyOf(buffer, buffer.length * 2);
                }
                if (!arrayDecoder.decodeNull()) {
                    buffer[index] = arrayDecoder.decodeChar();
                }
                index++;
            }
            arrayDecoder.finishStructure();
            return Arrays.copyOf(buffer, index);
        }

        @Override
        public void serialize(Encoder encoder,
                              EncoderContext context,
                              Argument<? extends char[]> type, char[] value) throws IOException {
            final Encoder arrayEncoder = encoder.encodeArray(type);
            for (char i : value) {
                arrayEncoder.encodeChar(i);
            }
            arrayEncoder.finishStructure();
        }

        @Override
        public boolean isEmpty(EncoderContext context, char[] value) {
            return value == null || value.length == 0;
        }

        @Override
        Argument<char[]> getType() {
            return Argument.of(char[].class);
        }
    }

    private static final class ByteArraySerde extends SerdeRegistrar<byte[]> {
        @Override
        public byte[] deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super byte[]> type)
                throws IOException {
            final Decoder arrayDecoder = decoder.decodeArray();
            byte[] buffer = new byte[100];
            int index = 0;
            while (arrayDecoder.hasNextArrayValue()) {
                if (buffer.length == index) {
                    buffer = Arrays.copyOf(buffer, buffer.length * 2);
                }
                if (!arrayDecoder.decodeNull()) {
                    buffer[index] = arrayDecoder.decodeByte();
                }
                index++;
            }
            arrayDecoder.finishStructure();
            return Arrays.copyOf(buffer, index);
        }

        @Override
        public void serialize(Encoder encoder,
                              EncoderContext context,
                              Argument<? extends byte[]> type, byte[] value) throws IOException {
            final Encoder arrayEncoder = encoder.encodeArray(type);
            for (byte i : value) {
                arrayEncoder.encodeByte(i);
            }
            arrayEncoder.finishStructure();
        }

        @Override
        public boolean isEmpty(EncoderContext context, byte[] value) {
            return value == null || value.length == 0;
        }

        @Override
        Argument<byte[]> getType() {
            return Argument.of(byte[].class);
        }
    }

    private static final class IntArraySerde extends SerdeRegistrar<int[]> {
        @Override
        public int[] deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super int[]> type)
                throws IOException {
            final Decoder arrayDecoder = decoder.decodeArray();
            int[] buffer = new int[50];
            int index = 0;
            while (arrayDecoder.hasNextArrayValue()) {
                if (buffer.length == index) {
                    buffer = Arrays.copyOf(buffer, buffer.length * 2);
                }
                if (!arrayDecoder.decodeNull()) {
                    buffer[index] = arrayDecoder.decodeInt();
                }
                index++;
            }
            arrayDecoder.finishStructure();
            return Arrays.copyOf(buffer, index);
        }

        @Override
        public void serialize(Encoder encoder,
                              EncoderContext context,
                              Argument<? extends int[]> type, int[] value) throws IOException {
            final Encoder arrayEncoder = encoder.encodeArray(type);
            for (int i : value) {
                arrayEncoder.encodeInt(i);
            }
            arrayEncoder.finishStructure();
        }

        @Override
        public boolean isEmpty(EncoderContext context, int[] value) {
            return value == null || value.length == 0;
        }

        @Override
        Argument<int[]> getType() {
            return Argument.of(int[].class);
        }
    }

    private static final class BigDecimalSerde extends SerdeRegistrar<BigDecimal> implements NumberSerde<BigDecimal> {

        @Override
        Argument<BigDecimal> getType() {
            return Argument.of(BigDecimal.class);
        }

        @Override
        public void serialize(Encoder encoder, EncoderContext context, Argument<? extends BigDecimal> type, BigDecimal value)
                throws IOException {
            encoder.encodeBigDecimal(value);
        }

        @Override
        public BigDecimal deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super BigDecimal> type)
                throws IOException {
            return decoder.decodeBigDecimal();
        }
    }

    private static final class StringSerde extends SerdeRegistrar<String> implements Serde<String> {

        @Override
        Argument<String> getType() {
            return Argument.of(String.class);
        }

        @Override
        public void serialize(Encoder encoder, EncoderContext context, Argument<? extends String> type, String value)
            throws IOException {
            encoder.encodeString(value);
        }

        @Override
        public String deserialize(Decoder decoder, DecoderContext context, Argument<? super String> type) throws IOException {
            return decoder.decodeString();
        }

        @Override
        public String deserializeNullable(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super String> type) throws IOException {
            return decoder.decodeStringNullable();
        }
    }


    private static final class URLSerde extends SerdeRegistrar<URL> {

        @Override
        Argument<URL> getType() {
            return Argument.of(URL.class);
        }

        @Override
        public void serialize(Encoder encoder, EncoderContext context, Argument<? extends URL> type, URL value)
                throws IOException {
            encoder.encodeString(value.toString());
        }

        @Override
        public URL deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super URL> type)
                throws IOException {
            return new URL(decoder.decodeString());
        }
    }

    private static final class URISerde extends SerdeRegistrar<URI> {

        @Override
        Argument<URI> getType() {
            return Argument.of(URI.class);
        }

        @Override
        public void serialize(Encoder encoder, EncoderContext context, Argument<? extends URI> type, URI value)
                throws IOException {
            encoder.encodeString(value.toString());
        }

        @Override
        public URI deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super URI> type)
                throws IOException {
            return URI.create(decoder.decodeString());
        }
    }

    private static final class CharsetSerde extends SerdeRegistrar<Charset> {

        @Override
        Argument<Charset> getType() {
            return Argument.of(Charset.class);
        }

        @Override
        public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Charset> type, Charset value)
                throws IOException {
            encoder.encodeString(value.name());
        }

        @Override
        public Charset deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Charset> type)
                throws IOException {
            return Charset.forName(decoder.decodeString());
        }
    }

    private static final class TimeZoneSerde extends SerdeRegistrar<TimeZone> {

        @Override
        Argument<TimeZone> getType() {
            return Argument.of(TimeZone.class);
        }

        @Override
        public void serialize(Encoder encoder, EncoderContext context, Argument<? extends TimeZone> type, TimeZone value)
                throws IOException {
            encoder.encodeString(value.getID());
        }

        @Override
        public TimeZone deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super TimeZone> type)
                throws IOException {
            return TimeZone.getTimeZone(decoder.decodeString());
        }
    }

    private static final class LocaleSerde extends SerdeRegistrar<Locale> {

        @Override
        Argument<Locale> getType() {
            return Argument.of(Locale.class);
        }

        @Override
        public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Locale> type, Locale value)
                throws IOException {
            encoder.encodeString(value.toLanguageTag());
        }

        @Override
        public Locale deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Locale> type)
                throws IOException {
            return StringUtils.parseLocale(decoder.decodeString());
        }
    }

    private static final class UUIDSerde extends SerdeRegistrar<UUID> {

        @Override
        Argument<UUID> getType() {
            return Argument.of(UUID.class);
        }

        @Override
        public void serialize(Encoder encoder, EncoderContext context, Argument<? extends UUID> type, UUID value)
                throws IOException {
            encoder.encodeString(value.toString());
        }

        @Override
        public UUID deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super UUID> type)
                throws IOException {
            return UUID.fromString(decoder.decodeString());
        }
    }

    private static final class BigIntegerSerde extends SerdeRegistrar<BigInteger> implements NumberSerde<BigInteger> {

        @Override
        Argument<BigInteger> getType() {
            return Argument.of(BigInteger.class);
        }

        @Override
        public void serialize(Encoder encoder, EncoderContext context, Argument<? extends BigInteger> type, BigInteger value)
                throws IOException {
            encoder.encodeBigInteger(value);
        }

        @Override
        public BigInteger deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super BigInteger> type)
                throws IOException {
            return decoder.decodeBigInteger();
        }
    }

    private static final class OptionalIntSerde extends SerdeRegistrar<OptionalInt> implements Serde<OptionalInt> {
        @Override
        public void serialize(Encoder encoder,
                              EncoderContext context,
                              Argument<? extends OptionalInt> type, OptionalInt value) throws IOException {
            if (value.isPresent()) {
                encoder.encodeInt(value.getAsInt());
            } else {
                encoder.encodeNull();
            }
        }

        @Override
        public OptionalInt deserialize(Decoder decoder, DecoderContext context, Argument<? super OptionalInt> type)
                throws IOException {
            if (decoder.decodeNull()) {
                return OptionalInt.empty();
            } else {
                return OptionalInt.of(
                        decoder.decodeInt()
                );
            }
        }

        @Override
        public OptionalInt deserializeNullable(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super OptionalInt> type) throws IOException {
            return deserialize(decoder, context, type);
        }

        @Override
        public OptionalInt getDefaultValue(DecoderContext context, Argument<? super OptionalInt> type) {
            return OptionalInt.empty();
        }

        @Override
        public boolean isEmpty(EncoderContext context, OptionalInt value) {
            return value == null || value.isEmpty();
        }

        @Override
        public boolean isAbsent(EncoderContext context, OptionalInt value) {
            return value == null || value.isEmpty();
        }

        @Override
        Argument<OptionalInt> getType() {
            return Argument.of(OptionalInt.class);
        }
    }

    private static final class OptionalDoubleSerde extends SerdeRegistrar<OptionalDouble> implements Serde<OptionalDouble> {
        @Override
        public void serialize(Encoder encoder,
                              EncoderContext context,
                              Argument<? extends OptionalDouble> type, OptionalDouble value) throws IOException {
            if (value.isPresent()) {
                encoder.encodeDouble(value.getAsDouble());
            } else {
                encoder.encodeNull();
            }
        }

        @Override
        public OptionalDouble deserialize(Decoder decoder,
                                          DecoderContext context,
                                          Argument<? super OptionalDouble> type) throws IOException {
            if (decoder.decodeNull()) {
                return OptionalDouble.empty();
            } else {
                return OptionalDouble.of(decoder.decodeDouble());
            }
        }

        @Override
        public OptionalDouble deserializeNullable(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super OptionalDouble> type) throws IOException {
            return deserialize(decoder, context, type);
        }

        @Override
        public boolean isEmpty(EncoderContext context, OptionalDouble value) {
            return value == null || value.isEmpty();
        }

        @Override
        public boolean isAbsent(EncoderContext context, OptionalDouble value) {
            return value == null || value.isEmpty();
        }

        @Override
        public OptionalDouble getDefaultValue(DecoderContext context, Argument<? super OptionalDouble> type) {
            return OptionalDouble.empty();
        }

        @Override
        Argument<OptionalDouble> getType() {
            return Argument.of(OptionalDouble.class);
        }
    }

    private static final class OptionalLongSerde extends SerdeRegistrar<OptionalLong> implements Serde<OptionalLong> {
        @Override
        public void serialize(Encoder encoder,
                              EncoderContext context,
                              Argument<? extends OptionalLong> type, OptionalLong value) throws IOException {
            if (value.isPresent()) {
                encoder.encodeLong(value.getAsLong());
            } else {
                encoder.encodeNull();
            }
        }

        @Override
        public OptionalLong deserialize(Decoder decoder, DecoderContext context, Argument<? super OptionalLong> type)
                throws IOException {
            if (decoder.decodeNull()) {
                return OptionalLong.empty();
            } else {
                return OptionalLong.of(decoder.decodeLong());
            }
        }

        @Override
        public OptionalLong deserializeNullable(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super OptionalLong> type) throws IOException {
            return deserialize(decoder, context, type);
        }

        @Override
        public OptionalLong getDefaultValue(DecoderContext context, Argument<? super OptionalLong> type) {
            return OptionalLong.empty();
        }

        @Override
        public boolean isEmpty(EncoderContext context, OptionalLong value) {
            return value == null || value.isEmpty();
        }

        @Override
        public boolean isAbsent(EncoderContext context, OptionalLong value) {
            return value == null || value.isEmpty();
        }

        @Override
        Argument<OptionalLong> getType() {
            return Argument.of(OptionalLong.class);
        }
    }
}
