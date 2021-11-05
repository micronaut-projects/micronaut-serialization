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
package io.micronaut.serde.serializers;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import io.micronaut.context.annotation.Factory;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.util.NullableSerde;
import jakarta.inject.Singleton;

/**
 * Factory class for core serializers.
 */
@Factory
public class CoreSerializers {

    /**
     * Generic array serializer.
     * @return A serializer for object array
     */
    @Singleton protected Serializer<Object[]> arraySerde() {
        return new Serializer<Object[]>() {
            @Override
            public void serialize(Encoder encoder,
                                  EncoderContext context,
                                  Object[] value,
                                  Argument<? extends Object[]> type) throws IOException {
                final Encoder arrayEncoder = encoder.encodeArray();
                // TODO: need better generics handling in core for arrays
                final Argument<?> componentType = Argument.of(type.getType().getComponentType());
                final Serializer<Object> componentSerializer =
                        (Serializer<Object>) context.findSerializer(componentType);
                for (Object v : value) {
                    componentSerializer.serialize(
                            arrayEncoder,
                            context,
                            v,
                            componentType
                    );
                }
                arrayEncoder.finishStructure();
            }

            @Override
            public boolean isEmpty(Object[] value) {
                return ArrayUtils.isEmpty(value);
            }
        };
    }

    /**
     * A serializer for the date type.
     *
     * @return A date serializer
     */
    @Singleton protected Serializer<Date> dateSerializer() {
        return (encoder, context, value, type) -> {
            context.findSerializer(Argument.LONG)
                    .serialize(
                            encoder,
                            context, value.getTime(),
                            Argument.LONG
                    );
        };
    }

    /**
     * A serializer for all instances of {@link java.lang.CharSequence}.
     *
     * @return A char sequence serializer
     */
    @Singleton protected Serializer<CharSequence> charSequenceSerializer() {
        return new Serializer<CharSequence>() {
            @Override
            public void serialize(Encoder encoder,
                                  EncoderContext context,
                                  CharSequence value,
                                  Argument<? extends CharSequence> type) throws IOException {
                encoder.encodeString(value.toString());
            }

            @Override
            public boolean isEmpty(CharSequence value) {
                return value == null || value.length() == 0;
            }
        };
    }

    /**
     * A serializer for all instances of {@link java.lang.Long}.
     *
     * @return A long serializer
     */
    @Singleton protected Serializer<Long> longSerializer() {
        return (encoder, context, value, type) -> encoder.encodeLong(value);
    }

    /**
     * A serializer for all instances of {@link java.lang.Double}.
     *
     * @return A double serializer
     */
    @Singleton protected Serializer<Double> doubleSerializer() {
        return (encoder, context, value, type) -> encoder.encodeDouble(value);
    }

    /**
     * A serializer for all instances of {@link java.lang.Float}.
     *
     * @return A float serializer
     */
    @Singleton protected Serializer<Float> floatSerializer() {
        return (encoder, context, value, type) -> encoder.encodeFloat(value);
    }

    /**
     * A serializer for all instances of {@link java.lang.Short}.
     *
     * @return A short serializer
     */
    @Singleton protected Serializer<Short> shortSerializer() {
        return (encoder, context, value, type) -> encoder.encodeShort(value);
    }

    /**
     * A serializer for all instances of {@link java.lang.Character}.
     *
     * @return A Character serializer
     */
    @Singleton protected Serializer<Character> charSerializer() {
        return (encoder, context, value, type) -> encoder.encodeChar(value);
    }

    /**
     * A serializer for all instances of {@code char[]}.
     *
     * @return An array serializer
     */
    @Singleton protected NullableSerde<char[]> charArraySerde() {
        return new NullableSerde<char[]>() {
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
                final Encoder arrayEncoder = encoder.encodeArray();
                for (char i : value) {
                    arrayEncoder.encodeChar(i);
                }
                arrayEncoder.finishStructure();
            }

            @Override
            public boolean isEmpty(char[] value) {
                return value == null || value.length == 0;
            }
        };
    }

    /**
     * A serializer for all instances of {@link java.lang.Byte}.
     *
     * @return A byte serializer
     */
    @Singleton protected Serializer<Byte> byteSerializer() {
        return (encoder, context, value, type) -> encoder.encodeByte(value);
    }

    /**
     * A serializer for all instances of {@code byte[]}.
     *
     * @return An array serializer
     */
    @Singleton protected NullableSerde<byte[]> byteArraySerde() {
        return new NullableSerde<byte[]>() {
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
                final Encoder arrayEncoder = encoder.encodeArray();
                for (byte i : value) {
                    arrayEncoder.encodeByte(i);
                }
                arrayEncoder.finishStructure();
            }

            @Override
            public boolean isEmpty(byte[] value) {
                return value == null || value.length == 0;
            }
        };
    }

    /**
     * A serializer for all instances of {@link java.lang.Integer}.
     *
     * @return A integer serializer
     */
    @Singleton protected Serializer<Integer> intSerializer() {
        return (encoder, context, value, type) -> encoder.encodeInt(value);
    }

    /**
     * A serializer for all instances of {@code int[]}.
     *
     * @return An array serializer
     */
    @Singleton protected NullableSerde<int[]> intArraySerde() {
        return new NullableSerde<int[]>() {
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
                final Encoder arrayEncoder = encoder.encodeArray();
                for (int i : value) {
                    arrayEncoder.encodeInt(i);
                }
                arrayEncoder.finishStructure();
            }

            @Override
            public boolean isEmpty(int[] value) {
                return value == null || value.length == 0;
            }
        };
    }

    /**
     * A serde for all instances of {@code short[]}.
     *
     * @return An array serializer
     */
    @Singleton protected NullableSerde<short[]> shortArraySerde() {
        return new NullableSerde<short[]>() {
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
                final Encoder arrayEncoder = encoder.encodeArray();
                for (short i : value) {
                    arrayEncoder.encodeShort(i);
                }
                arrayEncoder.finishStructure();
            }

            @Override
            public boolean isEmpty(short[] value) {
                return value == null || value.length == 0;
            }
        };
    }

    /**
     * A serializer for all instances of {@code boolean[]}.
     *
     * @return An array serializer
     */
    @Singleton protected NullableSerde<boolean[]> booleanArraySerde() {
        return new NullableSerde<boolean[]>() {
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
                final Encoder arrayEncoder = encoder.encodeArray();
                for (boolean i : value) {
                    arrayEncoder.encodeBoolean(i);
                }
                arrayEncoder.finishStructure();
            }

            @Override
            public boolean isEmpty(boolean[] value) {
                return value == null || value.length == 0;
            }
        };
    }

    /**
     * A serializer for all instances of {@code long[]}.
     *
     * @return An array serializer
     */
    @Singleton protected NullableSerde<long[]> longArraySerde() {
        return new NullableSerde<long[]>() {
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
                final Encoder arrayEncoder = encoder.encodeArray();
                for (long i : value) {
                    arrayEncoder.encodeLong(i);
                }
                arrayEncoder.finishStructure();
            }

            @Override
            public boolean isEmpty(long[] value) {
                return value == null || value.length == 0;
            }
        };
    }

    /**
     * A serializer for all instances of {@code float[]}.
     *
     * @return An array serializer
     */
    @Singleton protected NullableSerde<float[]> floatArraySerde() {
        return new NullableSerde<float[]>() {
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
                final Encoder arrayEncoder = encoder.encodeArray();
                for (float i : value) {
                    arrayEncoder.encodeFloat(i);
                }
                arrayEncoder.finishStructure();
            }

            @Override
            public boolean isEmpty(float[] value) {
                return value == null || value.length == 0;
            }
        };
    }

    /**
     * A serializer for all instances of {@code double[]}.
     *
     * @return An array serializer
     */
    @Singleton protected NullableSerde<double[]> doubleArraySerde() {
        return new NullableSerde<double[]>() {
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
                final Encoder arrayEncoder = encoder.encodeArray();
                for (double i : value) {
                    arrayEncoder.encodeDouble(i);
                }
                arrayEncoder.finishStructure();
            }

            @Override
            public boolean isEmpty(double[] value) {
                return value == null || value.length == 0;
            }
        };
    }

    /**
     * A serializer for all instances of {@link java.lang.Boolean}.
     *
     * @return A boolean serializer
     */
    @Singleton protected Serializer<Boolean> booleanSerializer() {
        return (encoder, context, value, type) -> encoder.encodeBoolean(value);
    }

    /**
     * A serializer for all instances of {@link java.lang.Iterable}.
     *
     * @param <T> The element type
     * @return An iterable serializer
     */
    @Singleton protected <T> Serializer<Iterable<T>> iterableSerializer() {
        return new Serializer<Iterable<T>>() {
            @Override
            public void serialize(Encoder encoder,
                                  EncoderContext context,
                                  Iterable<T> value,
                                  Argument<? extends Iterable<T>> type) throws IOException {
                final Encoder childEncoder = encoder.encodeArray();
                final Argument[] generics = type.getTypeParameters();
                if (ArrayUtils.isEmpty(generics)) {
                    throw new SerdeException("Serializing raw iterables is not supported for value: " + value);
                }
                final Argument<T> generic = (Argument<T>) generics[0];
                final Serializer<T> componentSerializer = (Serializer<T>) context.findSerializer(generic);
                for (T t : value) {
                    if (t == null) {
                        encoder.encodeNull();
                        continue;
                    }
                    componentSerializer.serialize(
                            childEncoder,
                            context,
                            t,
                            generic
                    );
                }
                childEncoder.finishStructure();
            }

            @Override
            public boolean isEmpty(Iterable<T> value) {
                if (value == null) {
                    return true;
                }
                if (value instanceof Collection) {
                    return ((Collection<T>) value).isEmpty();
                } else {
                    return !value.iterator().hasNext();
                }
            }
        };
    }

    /**
     * A serializer for all instances of {@link java.util.Optional}.
     *
     * @param <T> The optional type
     * @return An Optional serializer
     */
    @Singleton protected <T> Serializer<Optional<T>> optionalSerializer() {
        return new Serializer<Optional<T>>() {
            @Override
            public void serialize(Encoder encoder,
                                  EncoderContext context,
                                  Optional<T> value,
                                  Argument<? extends Optional<T>> type) throws IOException {
                final Argument[] generics = type.getTypeParameters();
                if (ArrayUtils.isEmpty(generics)) {
                    throw new SerdeException("Serializing raw optionals is not supported for value: " + value);
                }
                final T o = value.orElse(null);
                if (o != null) {
                    final Argument<T> generic = (Argument<T>) generics[0];
                    final Serializer<T> componentSerializer = (Serializer<T>) context.findSerializer(generic);
                    componentSerializer.serialize(
                            encoder,
                            context,
                            o,
                            generic
                    );
                } else {
                    encoder.encodeNull();
                }
            }

            @Override
            public boolean isEmpty(Optional<T> value) {
                return value == null || !value.isPresent();
            }

            @Override
            public boolean isAbsent(Optional<T> value) {
                return value == null || !value.isPresent();
            }
        };
    }

    /**
     * A serializer for maps.
     *
     * @param <K>  The key type
     * @param <V>  The value type
     * @return A bit decimal serializer
     */
    @Singleton protected <K extends CharSequence, V> Serializer<Map<K, V>> mapSerializer() {
        return new Serializer<Map<K, V>>() {
            @Override
            public void serialize(Encoder encoder,
                                  EncoderContext context,
                                  Map<K, V> value,
                                  Argument<? extends Map<K, V>> type) throws IOException {
                final Encoder childEncoder = encoder.encodeObject();
                final Argument[] generics = type.getTypeParameters();
                if (ArrayUtils.isEmpty(generics) || generics.length != 2) {
                    throw new SerdeException("Serializing raw maps is not supported for value: " + value);
                }
                final Argument<V> valueGeneric = (Argument<V>) generics[1];
                final Serializer<V> valSerializer = (Serializer<V>) context.findSerializer(valueGeneric);
                for (K k : value.keySet()) {
                    childEncoder.encodeKey(k.toString());
                    final V v = value.get(k);
                    if (v == null) {
                        childEncoder.encodeNull();
                    } else {
                        valSerializer.serialize(
                                encoder,
                                context,
                                v,
                                valueGeneric
                        );
                    }
                }
                childEncoder.finishStructure();
            }

            @Override
            public boolean isEmpty(Map<K, V> value) {
                return CollectionUtils.isEmpty(value);
            }
        };
    }
}
