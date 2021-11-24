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
package io.micronaut.serde;

import io.micronaut.context.BeanContext;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.serde.deserializers.ObjectDeserializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.serdes.NumberSerde;
import io.micronaut.serde.serializers.ObjectSerializer;
import io.micronaut.serde.util.NullableDeserializer;
import io.micronaut.serde.util.NullableSerde;
import io.micronaut.serde.util.TypeKey;
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
import java.util.stream.Stream;

/**
 * Default implementation of the {@link io.micronaut.serde.SerdeRegistry} interface.
 */
@Singleton
public class DefaultSerdeRegistry implements SerdeRegistry {

    private final Serializer<Object> objectSerializer;
    private final Map<Class<?>, List<BeanDefinition<Serializer>>> serializerDefMap;
    private final Map<Class<?>, List<BeanDefinition<Deserializer>>> deserializerDefMap;
    private final Map<TypeKey, Serializer<?>> serializerMap = new ConcurrentHashMap<>(50);
    private final Map<TypeKey, Deserializer<?>> deserializerMap = new ConcurrentHashMap<>(50);
    private final BeanContext beanContext;
    private final SerdeIntrospections introspections;
    private final Deserializer<Object> objectDeserializer;
    private final Serde<Object[]> objectArraySerde;

    public DefaultSerdeRegistry(
            BeanContext beanContext,
            ObjectSerializer objectSerializer,
            ObjectDeserializer objectDeserializer,
            Serde<Object[]> objectArraySerde,
            SerdeIntrospections introspections) {
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
        registerPrimitiveSerdes();
        this.objectSerializer = objectSerializer;
        this.objectDeserializer = objectDeserializer;
    }

    private void registerPrimitiveSerdes() {
        this.deserializerMap.put(
                new TypeKey(Argument.BOOLEAN),
                (decoder, decoderContext, type) -> decoder.decodeBoolean()
        );
        this.deserializerMap.put(
                new TypeKey(Argument.of(Boolean.class)),
                (NullableDeserializer<Boolean>) (decoder, decoderContext, type) -> decoder.decodeBoolean()
        );
        this.deserializerMap.put(
                new TypeKey(Argument.CHAR),
                (decoder, decoderContext, type) -> decoder.decodeChar()
        );
        this.deserializerMap.put(
                new TypeKey(Argument.of(Character.class)),
                (NullableDeserializer<Character>) (decoder, decoderContext, type) -> decoder.decodeChar()
        );
    }

    private void registerBuiltInSerdes() {
        this.deserializerMap.put(new TypeKey(Argument.STRING),
                                 (NullableDeserializer<String>) (decoder, decoderContext, type) -> decoder.decodeString());
        Stream.of(
                new IntegerSerde(),
                new LongSerde(),
                new ShortSerde(),
                new FloatSerde(),
                new ByteSerde(),
                new DoubleSerde(),
                new OptionalIntSerde(),
                new OptionalDoubleSerde(),
                new OptionalLongSerde(),
                new BigDecimalSerde(),
                new BigIntegerSerde(),
                new UUIDSerde(),
                new URLSerde(),
                new URISerde(),
                new CharsetSerde(),
                new TimeZoneSerde(),
                new LocaleSerde(),
                new IntArraySerde(),
                new LongArraySerde(),
                new FloatArraySerde(),
                new ShortArraySerde(),
                new DoubleArraySerde(),
                new BooleanArraySerde(),
                new ByteArraySerde(),
                new CharArraySerde()
        ).forEach(SerdeRegistrar::register);
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
    public <T> Deserializer<? extends T> findDeserializer(Argument<? extends T> type) {
        Objects.requireNonNull(type, "Type cannot be null");
        final TypeKey key = new TypeKey(type);
        final Deserializer<?> deserializer = deserializerMap.get(key);
        if (deserializer != null) {
            return (Deserializer<? extends T>) deserializer;
        } else {
            Deserializer<?> deser = beanContext.findBean(Argument.of(Deserializer.class, type))
                    .orElse(null);
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
                    throw new SerdeException("Multiple possible serializers found for type [" + type + "]: " + possibles);
                }
            } else {
                serializerMap.put(key, objectSerializer);
            }
        }
        return objectSerializer;
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

    private final class ByteSerde extends SerdeRegistrar<Byte> implements NumberSerde<Byte> {
        @Override
        public Byte deserializeNonNull(Decoder decoder,
                                         DecoderContext decoderContext,
                                         Argument<? super Byte> type) throws IOException {
            return decoder.decodeByte();
        }

        @Override
        public void serialize(Encoder encoder,
                              EncoderContext context,
                              Byte value,
                              Argument<? extends Byte> type) throws IOException {
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
    }

    private final class DoubleSerde extends SerdeRegistrar<Double> implements NumberSerde<Double> {
        @Override
        public Double deserializeNonNull(Decoder decoder,
                                        DecoderContext decoderContext,
                                        Argument<? super Double> type) throws IOException {
            return decoder.decodeDouble();
        }

        @Override
        public void serialize(Encoder encoder,
                              EncoderContext context,
                              Double value,
                              Argument<? extends Double> type) throws IOException {
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
    }

    private final class ShortSerde extends SerdeRegistrar<Short> implements NumberSerde<Short> {
        @Override
        public Short deserializeNonNull(Decoder decoder,
                                          DecoderContext decoderContext,
                                          Argument<? super Short> type) throws IOException {
            return decoder.decodeShort();
        }

        @Override
        public void serialize(Encoder encoder,
                              EncoderContext context,
                              Short value,
                              Argument<? extends Short> type) throws IOException {
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
    }

    private final class FloatSerde extends SerdeRegistrar<Float> implements NumberSerde<Float> {
        @Override
        public Float deserializeNonNull(Decoder decoder,
                                        DecoderContext decoderContext,
                                        Argument<? super Float> type) throws IOException {
            return decoder.decodeFloat();
        }

        @Override
        public void serialize(Encoder encoder,
                              EncoderContext context,
                              Float value,
                              Argument<? extends Float> type) throws IOException {
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
    }

    private final class IntegerSerde extends SerdeRegistrar<Integer> implements NumberSerde<Integer> {
        @Override
        public Integer deserializeNonNull(Decoder decoder,
                                          DecoderContext decoderContext,
                                          Argument<? super Integer> type) throws IOException {
            return decoder.decodeInt();
        }

        @Override
        public void serialize(Encoder encoder,
                              EncoderContext context,
                              Integer value,
                              Argument<? extends Integer> type) throws IOException {
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
    }

    private final class LongSerde extends SerdeRegistrar<Long> implements NumberSerde<Long> {
        @Override
        public Long deserializeNonNull(Decoder decoder,
                                          DecoderContext decoderContext,
                                          Argument<? super Long> type) throws IOException {
            return decoder.decodeLong();
        }

        @Override
        public void serialize(Encoder encoder,
                              EncoderContext context,
                              Long value,
                              Argument<? extends Long> type) throws IOException {
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
    }

    private abstract class SerdeRegistrar<T> implements Serde<T> {
        @NonNull
        abstract Argument<T> getType();

        @NonNull
        protected Iterable<Argument<?>> getTypes() {
            return Collections.singleton(getType());
        }

        void register() {
            for (Argument<?> type : getTypes()) {
                final TypeKey typeEntry = new TypeKey(type);
                DefaultSerdeRegistry.this.deserializerMap
                        .put(typeEntry, this);
                DefaultSerdeRegistry.this.serializerMap
                        .put(typeEntry, this);
            }
        }
    }

    private final class BooleanArraySerde extends SerdeRegistrar<boolean[]> implements NullableSerde<boolean[]> {
        @Override
        public boolean[] deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super boolean[]> type)
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
                              boolean[] value,
                              Argument<? extends boolean[]> type) throws IOException {
            final Encoder arrayEncoder = encoder.encodeArray(type);
            for (boolean i : value) {
                arrayEncoder.encodeBoolean(i);
            }
            arrayEncoder.finishStructure();
        }

        @Override
        public boolean isEmpty(boolean[] value) {
            return value == null || value.length == 0;
        }

        @Override
        Argument<boolean[]> getType() {
            return Argument.of(boolean[].class);
        }
    }

    private final class DoubleArraySerde extends SerdeRegistrar<double[]> implements NullableSerde<double[]> {
        @Override
        public double[] deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super double[]> type)
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
                              double[] value,
                              Argument<? extends double[]> type) throws IOException {
            final Encoder arrayEncoder = encoder.encodeArray(type);
            for (double i : value) {
                arrayEncoder.encodeDouble(i);
            }
            arrayEncoder.finishStructure();
        }

        @Override
        public boolean isEmpty(double[] value) {
            return value == null || value.length == 0;
        }

        @Override
        Argument<double[]> getType() {
            return Argument.of(double[].class);
        }
    }

    private final class ShortArraySerde extends SerdeRegistrar<short[]> implements NullableSerde<short[]> {
        @Override
        public short[] deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super short[]> type)
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
                              short[] value,
                              Argument<? extends short[]> type) throws IOException {
            final Encoder arrayEncoder = encoder.encodeArray(type);
            for (short i : value) {
                arrayEncoder.encodeShort(i);
            }
            arrayEncoder.finishStructure();
        }

        @Override
        public boolean isEmpty(short[] value) {
            return value == null || value.length == 0;
        }

        @Override
        Argument<short[]> getType() {
            return Argument.of(short[].class);
        }
    }

    private final class FloatArraySerde extends SerdeRegistrar<float[]> implements NullableSerde<float[]> {
        @Override
        public float[] deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super float[]> type)
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
                              float[] value,
                              Argument<? extends float[]> type) throws IOException {
            final Encoder arrayEncoder = encoder.encodeArray(type);
            for (float i : value) {
                arrayEncoder.encodeFloat(i);
            }
            arrayEncoder.finishStructure();
        }

        @Override
        public boolean isEmpty(float[] value) {
            return value == null || value.length == 0;
        }

        @Override
        Argument<float[]> getType() {
            return Argument.of(float[].class);
        }
    }

    private final class LongArraySerde extends SerdeRegistrar<long[]> implements NullableSerde<long[]> {

        @Override
        public long[] deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super long[]> type)
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
                              long[] value,
                              Argument<? extends long[]> type) throws IOException {
            final Encoder arrayEncoder = encoder.encodeArray(type);
            for (long i : value) {
                arrayEncoder.encodeLong(i);
            }
            arrayEncoder.finishStructure();
        }

        @Override
        public boolean isEmpty(long[] value) {
            return value == null || value.length == 0;
        }

        @Override
        Argument<long[]> getType() {
            return Argument.of(long[].class);
        }
    }

    private final class CharArraySerde extends SerdeRegistrar<char[]> implements NullableSerde<char[]> {
        @Override
        public char[] deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super char[]> type)
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
                              char[] value,
                              Argument<? extends char[]> type) throws IOException {
            final Encoder arrayEncoder = encoder.encodeArray(type);
            for (char i : value) {
                arrayEncoder.encodeChar(i);
            }
            arrayEncoder.finishStructure();
        }

        @Override
        public boolean isEmpty(char[] value) {
            return value == null || value.length == 0;
        }

        @Override
        Argument<char[]> getType() {
            return Argument.of(char[].class);
        }
    }

    private final class ByteArraySerde extends SerdeRegistrar<byte[]> implements NullableSerde<byte[]> {
        @Override
        public byte[] deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super byte[]> type)
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
                              byte[] value,
                              Argument<? extends byte[]> type) throws IOException {
            final Encoder arrayEncoder = encoder.encodeArray(type);
            for (byte i : value) {
                arrayEncoder.encodeByte(i);
            }
            arrayEncoder.finishStructure();
        }

        @Override
        public boolean isEmpty(byte[] value) {
            return value == null || value.length == 0;
        }

        @Override
        Argument<byte[]> getType() {
            return Argument.of(byte[].class);
        }
    }

    private final class IntArraySerde extends SerdeRegistrar<int[]> implements NullableSerde<int[]> {
        @Override
        public int[] deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super int[]> type)
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
                              int[] value,
                              Argument<? extends int[]> type) throws IOException {
            final Encoder arrayEncoder = encoder.encodeArray(type);
            for (int i : value) {
                arrayEncoder.encodeInt(i);
            }
            arrayEncoder.finishStructure();
        }

        @Override
        public boolean isEmpty(int[] value) {
            return value == null || value.length == 0;
        }

        @Override
        Argument<int[]> getType() {
            return Argument.of(int[].class);
        }
    }

    private final class BigDecimalSerde
            extends SerdeRegistrar<BigDecimal>
            implements NumberSerde<BigDecimal> {

        @Override
        Argument<BigDecimal> getType() {
            return Argument.of(BigDecimal.class);
        }

        @Override
        public void serialize(Encoder encoder, EncoderContext context, BigDecimal value, Argument<? extends BigDecimal> type)
                throws IOException {
            encoder.encodeBigDecimal(value);
        }

        @Override
        public BigDecimal deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super BigDecimal> type)
                throws IOException {
            return decoder.decodeBigDecimal();
        }
    }

    private final class URLSerde extends SerdeRegistrar<URL> implements NullableSerde<URL> {

        @Override
        Argument<URL> getType() {
            return Argument.of(URL.class);
        }

        @Override
        public void serialize(Encoder encoder, EncoderContext context, URL value, Argument<? extends URL> type)
                throws IOException {
            encoder.encodeString(value.toString());
        }

        @Override
        public URL deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super URL> type)
                throws IOException {
            return new URL(decoder.decodeString());
        }
    }

    private final class URISerde extends SerdeRegistrar<URI> implements NullableSerde<URI> {

        @Override
        Argument<URI> getType() {
            return Argument.of(URI.class);
        }

        @Override
        public void serialize(Encoder encoder, EncoderContext context, URI value, Argument<? extends URI> type)
                throws IOException {
            encoder.encodeString(value.toString());
        }

        @Override
        public URI deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super URI> type)
                throws IOException {
            return URI.create(decoder.decodeString());
        }
    }

    private final class CharsetSerde extends SerdeRegistrar<Charset> implements NullableSerde<Charset> {

        @Override
        Argument<Charset> getType() {
            return Argument.of(Charset.class);
        }

        @Override
        public void serialize(Encoder encoder, EncoderContext context, Charset value, Argument<? extends Charset> type)
                throws IOException {
            encoder.encodeString(value.name());
        }

        @Override
        public Charset deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super Charset> type)
                throws IOException {
            return Charset.forName(decoder.decodeString());
        }
    }

    private final class TimeZoneSerde extends SerdeRegistrar<TimeZone> implements NullableSerde<TimeZone> {

        @Override
        Argument<TimeZone> getType() {
            return Argument.of(TimeZone.class);
        }

        @Override
        public void serialize(Encoder encoder, EncoderContext context, TimeZone value, Argument<? extends TimeZone> type)
                throws IOException {
            encoder.encodeString(value.getID());
        }

        @Override
        public TimeZone deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super TimeZone> type)
                throws IOException {
            return TimeZone.getTimeZone(decoder.decodeString());
        }
    }

    private final class LocaleSerde extends SerdeRegistrar<Locale> implements NullableSerde<Locale> {

        @Override
        Argument<Locale> getType() {
            return Argument.of(Locale.class);
        }

        @Override
        public void serialize(Encoder encoder, EncoderContext context, Locale value, Argument<? extends Locale> type)
                throws IOException {
            encoder.encodeString(value.toLanguageTag());
        }

        @Override
        public Locale deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super Locale> type)
                throws IOException {
            return StringUtils.parseLocale(decoder.decodeString());
        }
    }

    private final class UUIDSerde extends SerdeRegistrar<UUID> implements NullableSerde<UUID> {

        @Override
        Argument<UUID> getType() {
            return Argument.of(UUID.class);
        }

        @Override
        public void serialize(Encoder encoder, EncoderContext context, UUID value, Argument<? extends UUID> type)
                throws IOException {
            encoder.encodeString(value.toString());
        }

        @Override
        public UUID deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super UUID> type)
                throws IOException {
            return UUID.fromString(decoder.decodeString());
        }
    }

    private final class BigIntegerSerde
            extends SerdeRegistrar<BigInteger>
            implements NumberSerde<BigInteger> {

        @Override
        Argument<BigInteger> getType() {
            return Argument.of(BigInteger.class);
        }

        @Override
        public void serialize(Encoder encoder, EncoderContext context, BigInteger value, Argument<? extends BigInteger> type)
                throws IOException {
            encoder.encodeBigInteger(value);
        }

        @Override
        public BigInteger deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super BigInteger> type)
                throws IOException {
            return decoder.decodeBigInteger();
        }
    }

    private final class OptionalIntSerde extends SerdeRegistrar<OptionalInt> implements Serde<OptionalInt> {
        @Override
        public void serialize(Encoder encoder,
                              EncoderContext context,
                              OptionalInt value,
                              Argument<? extends OptionalInt> type) throws IOException {
            if (value.isPresent()) {
                encoder.encodeInt(value.getAsInt());
            } else {
                encoder.encodeNull();
            }
        }

        @Override
        public OptionalInt deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super OptionalInt> type)
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
        public OptionalInt getDefaultValue() {
            return OptionalInt.empty();
        }

        @Override
        public boolean isEmpty(OptionalInt value) {
            return value == null || !value.isPresent();
        }

        @Override
        public boolean isAbsent(OptionalInt value) {
            return value == null || !value.isPresent();
        }

        @Override
        Argument<OptionalInt> getType() {
            return Argument.of(OptionalInt.class);
        }
    }

    private final class OptionalDoubleSerde extends SerdeRegistrar<OptionalDouble> implements Serde<OptionalDouble> {
        @Override
        public void serialize(Encoder encoder,
                              EncoderContext context,
                              OptionalDouble value,
                              Argument<? extends OptionalDouble> type) throws IOException {
            if (value.isPresent()) {
                encoder.encodeDouble(value.getAsDouble());
            } else {
                encoder.encodeNull();
            }
        }

        @Override
        public OptionalDouble deserialize(Decoder decoder,
                                          DecoderContext decoderContext,
                                          Argument<? super OptionalDouble> type)
                throws IOException {
            if (decoder.decodeNull()) {
                return OptionalDouble.empty();
            } else {
                return OptionalDouble.of(
                        decoder.decodeDouble()
                );
            }
        }

        @Override
        public boolean isEmpty(OptionalDouble value) {
            return value == null || !value.isPresent();
        }

        @Override
        public boolean isAbsent(OptionalDouble value) {
            return value == null || !value.isPresent();
        }

        @Override
        public OptionalDouble getDefaultValue() {
            return OptionalDouble.empty();
        }

        @Override
        Argument<OptionalDouble> getType() {
            return Argument.of(OptionalDouble.class);
        }
    }

    private final class OptionalLongSerde extends SerdeRegistrar<OptionalLong> implements Serde<OptionalLong> {
        @Override
        public void serialize(Encoder encoder,
                              EncoderContext context,
                              OptionalLong value,
                              Argument<? extends OptionalLong> type) throws IOException {
            if (value.isPresent()) {
                encoder.encodeLong(value.getAsLong());
            } else {
                encoder.encodeNull();
            }
        }

        @Override
        public OptionalLong deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super OptionalLong> type)
                throws IOException {
            if (decoder.decodeNull()) {
                return OptionalLong.empty();
            } else {
                return OptionalLong.of(
                        decoder.decodeLong()
                );
            }
        }

        @Override
        public OptionalLong getDefaultValue() {
            return OptionalLong.empty();
        }

        @Override
        public boolean isEmpty(OptionalLong value) {
            return value == null || !value.isPresent();
        }

        @Override
        public boolean isAbsent(OptionalLong value) {
            return value == null || !value.isPresent();
        }

        @Override
        Argument<OptionalLong> getType() {
            return Argument.of(OptionalLong.class);
        }
    }
}
