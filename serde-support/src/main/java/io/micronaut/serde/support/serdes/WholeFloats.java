/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.FormatConfiguration;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.SerializationConfiguration;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.support.util.SerdeFeatures;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

/**
 * Support for {@link SerializationConfiguration.Feature#WRITE_WHOLE_FLOATS_AS_INTEGERS}. A serde asks
 * {@link #enabled(Serializer.EncoderContext, Argument)} once, when it is specialized, and answers a serializer that
 * writes with {@link #encode(Encoder, double)}, so that nothing is checked for a value while the feature is off.
 *
 * @since 3.3.0
 */
@Internal
final class WholeFloats {

    /**
     * Every whole number of this magnitude or less is a {@code double}. It is {@code Number.MAX_SAFE_INTEGER + 1}
     * of JavaScript.
     */
    private static final long MAX_WHOLE = 1L << 53;

    private WholeFloats() {
    }

    /**
     * @param context The encoder context
     * @param type    The type being written, whose annotations can switch the feature or set a number shape
     * @return Whether whole floating-point values are written as integers
     */
    static boolean enabled(Serializer.EncoderContext context, Argument<?> type) {
        AnnotationMetadata annotationMetadata = type.getAnnotationMetadata();
        if (annotationMetadata.isEmpty()) {
            return enabled(context);
        }
        FormatConfiguration.Shape shape = annotationMetadata
            .enumValue(SerdeConfig.class, SerdeConfig.SHAPE, FormatConfiguration.Shape.class)
            .orElse(FormatConfiguration.Shape.ANY);
        return enabled(SerdeFeatures.withFeatures(context, annotationMetadata), shape);
    }

    /**
     * The shape of a property wins over the feature: {@link FormatConfiguration.Shape#NUMBER_INT} writes whole
     * values as integers and {@link FormatConfiguration.Shape#NUMBER_FLOAT} keeps the fraction.
     *
     * @param context The encoder context
     * @param shape   The shape of the format of the property
     * @return Whether whole floating-point values are written as integers
     */
    static boolean enabled(Serializer.EncoderContext context, FormatConfiguration.Shape shape) {
        return switch (shape) {
            case NUMBER_INT -> true;
            case NUMBER_FLOAT -> false;
            default -> enabled(context);
        };
    }

    /**
     * @param context The encoder context
     * @return Whether whole floating-point values are written as integers
     */
    static boolean enabled(Serializer.EncoderContext context) {
        return context.getFeatures().contains(SerializationConfiguration.Feature.WRITE_WHOLE_FLOATS_AS_INTEGERS);
    }

    /**
     * Writes a whole value of magnitude at most 2<sup>53</sup> as an integer and any other value as it is.
     * The comparison is false for {@code NaN}, an infinity and a fraction; negative zero is written as {@code 0}.
     *
     * @param encoder The encoder
     * @param value   The value
     * @throws IOException If an I/O error occurs
     */
    static void encode(Encoder encoder, double value) throws IOException {
        long whole = (long) value;
        if (whole == value && whole >= -MAX_WHOLE && whole <= MAX_WHOLE) {
            encoder.encodeLong(whole);
        } else {
            encoder.encodeDouble(value);
        }
    }

    /**
     * Writes a whole value of magnitude at most 2<sup>53</sup> as an integer and any other value as it is.
     *
     * @param encoder The encoder
     * @param value   The value
     * @throws IOException If an I/O error occurs
     */
    static void encode(Encoder encoder, float value) throws IOException {
        long whole = (long) value;
        if (whole == value && whole >= -MAX_WHOLE && whole <= MAX_WHOLE) {
            encoder.encodeLong(whole);
        } else {
            encoder.encodeFloat(value);
        }
    }

    /**
     * Writes a {@link Double} or a {@link Float} with the feature, and any other number with the serde of its
     * property. It is not a {@code DecoderValueKind.Provider}, so that a bean serializer does not write the value
     * directly.
     *
     * @param <N> The number type
     */
    static final class NumberSerializer<N extends Number> implements Serializer<N> {
        private final Serializer<N> serializer;

        NumberSerializer(Serializer<N> serializer) {
            this.serializer = serializer;
        }

        @Override
        public void serialize(Encoder encoder, EncoderContext context, Argument<? extends N> type, N value) throws IOException {
            switch ((Number) value) {
                case Double aDouble -> encode(encoder, aDouble.doubleValue());
                case Float aFloat -> encode(encoder, aFloat.floatValue());
                default -> serializer.serialize(encoder, context, type, value);
            }
        }

        @Override
        public boolean isEmpty(EncoderContext context, @Nullable N value) {
            return serializer.isEmpty(context, value);
        }

        @Override
        public boolean isAbsent(EncoderContext context, @Nullable N value) {
            return serializer.isAbsent(context, value);
        }

        @Override
        public boolean isDefault(EncoderContext context, N value) {
            return serializer.isDefault(context, value);
        }
    }
}
