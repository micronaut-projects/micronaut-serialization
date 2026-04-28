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
package io.micronaut.serde.support.serdes;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.FormatConfiguration;
import io.micronaut.serde.FormattedSerde;
import io.micronaut.serde.Serde;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.util.SerdeFeatures;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Number serializer that handles formatting.
 * @param <N> The number type
 */
@Internal
public interface NumberSerde<N extends Number> extends FormattedSerde<N>, Serde<N> {

    /**
     * Encode the given number.
     *
     * @param encoder The encoder
     * @param value The number value
     * @throws IOException If an I/O error occurs
     * @since 3.0
     */
    default void encodeNumber(@NonNull Encoder encoder, @NonNull Number value) throws IOException {
        switch (value) {
            case Integer integer -> encoder.encodeInt(integer);
            case Long aLong -> encoder.encodeLong(aLong);
            case Double aDouble -> encoder.encodeDouble(aDouble);
            case Float aFloat -> encoder.encodeFloat(aFloat);
            case Byte aByte -> encoder.encodeByte(aByte);
            case Short aShort -> encoder.encodeShort(aShort);
            case BigDecimal bigDecimal -> encoder.encodeBigDecimal(bigDecimal);
            case BigInteger bigInteger -> encoder.encodeBigInteger(bigInteger);
            default -> throw new SerdeException("Unrecognized Number type: " + value.getClass().getName() + " " + value);
        }
    }

    @Override
    default Deserializer<N> createSpecific(DecoderContext decoderContext, Argument<? super N> context) throws SerdeException {
        decoderContext = SerdeFeatures.withFeatures(decoderContext, context.getAnnotationMetadata());
        FormatConfiguration format = FormatConfiguration.from(context.getAnnotationMetadata());
        return format == null ? this : createSpecific(decoderContext, context, format);
    }

    @Override
    default Deserializer<N> createSpecific(@NonNull DecoderContext context,
                                           @NonNull Argument<? super N> type,
                                           @NonNull FormatConfiguration format) throws SerdeException {
        return createSpecificSerde(format);
    }

    @Override
    default Serializer<N> createSpecific(EncoderContext context, Argument<? extends N> type) throws SerdeException {
        context = SerdeFeatures.withFeatures(context, type.getAnnotationMetadata());
        FormatConfiguration format = FormatConfiguration.from(type.getAnnotationMetadata());
        return format == null ? this : createSpecific(context, type, format);
    }

    @Override
    default Serializer<N> createSpecific(@NonNull EncoderContext context,
                                         @NonNull Argument<? extends N> type,
                                         @NonNull FormatConfiguration format) throws SerdeException {
        return createSpecificSerde(format);
    }

    private Serde<N> createSpecificSerde(@NonNull FormatConfiguration format) {
        if (format.pattern() != null) {
            return new FormattedNumberSerde<>(format);
        }
        if (format.shape() == FormatConfiguration.Shape.STRING) {
            return new StringShapeNumberSerde<>(format.radix(), this);
        }
        return this;
    }
}

final class StringShapeNumberSerde<N extends Number> implements FormattedSerde<N> {
    private final int radix;
    @Nullable
    private final Serializer<N> delegate;

    StringShapeNumberSerde(int radix) {
        this(radix, null);
    }

    StringShapeNumberSerde(int radix, @Nullable Serializer<N> delegate) {
        this.radix = radix;
        this.delegate = delegate;
    }

    @Override
    public void serialize(@NonNull Encoder encoder,
                          @NonNull EncoderContext context,
                          @NonNull Argument<? extends N> type,
                          @NonNull N value) throws IOException {
        encoder.encodeString(encodeNumber(value, type.getType(), radix));
    }

    @Override
    public N deserialize(@NonNull Decoder decoder,
                         @NonNull DecoderContext context,
                         @NonNull Argument<? super N> type) throws IOException {
        String value = decoder.decodeString();
        try {
            if (radix != FormatConfiguration.DEFAULT_RADIX && isIntegralNumber(type.getType())) {
                return (N) decodeIntegralNumber(value, type.getType(), radix);
            }
            if (type.getType() == Number.class) {
                return (N) new BigDecimal(value);
            }
            return (N) context.getConversionService().convertRequired(value, (Argument) type);
        } catch (ConversionErrorException | NumberFormatException e) {
            throw new SerdeException("Error decoding number of type " + type + " using string shape: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isEmpty(@NonNull EncoderContext context, @Nullable N value) {
        return delegate == null ? value == null : delegate.isEmpty(context, value);
    }

    @Override
    public boolean isAbsent(@NonNull EncoderContext context, @Nullable N value) {
        return delegate == null ? value == null : delegate.isAbsent(context, value);
    }

    @Override
    public boolean isDefault(@NonNull EncoderContext context, @NonNull N value) {
        return delegate != null && delegate.isDefault(context, value);
    }

    private static String encodeNumber(Object value, Class<?> type, int radix) {
        if (radix == FormatConfiguration.DEFAULT_RADIX || !isIntegralNumber(type)) {
            return value.toString();
        }
        if (value instanceof BigInteger bigInteger) {
            return bigInteger.toString(radix);
        }
        return Long.toString(((Number) value).longValue(), radix);
    }

    private static Object decodeIntegralNumber(String value, Class<?> type, int radix) {
        if (type == byte.class || type == Byte.class) {
            return Byte.parseByte(value, radix);
        }
        if (type == short.class || type == Short.class) {
            return Short.parseShort(value, radix);
        }
        if (type == int.class || type == Integer.class) {
            return Integer.parseInt(value, radix);
        }
        if (type == long.class || type == Long.class) {
            return Long.parseLong(value, radix);
        }
        if (type == BigInteger.class) {
            return new BigInteger(value, radix);
        }
        throw new NumberFormatException("Unsupported integral number type: " + type.getName());
    }

    private static boolean isIntegralNumber(Class<?> type) {
        return type == byte.class
            || type == Byte.class
            || type == short.class
            || type == Short.class
            || type == int.class
            || type == Integer.class
            || type == long.class
            || type == Long.class
            || type == BigInteger.class;
    }
}
