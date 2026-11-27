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
package io.micronaut.serde.util;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.SerializationConfiguration;

import java.io.IOException;
import java.util.Set;

/**
 * {@link SerializationConfiguration.Feature#WRITE_WHOLE_FLOATS_AS_INTEGERS} for the built-in and the generated
 * serializers. A serializer asks {@link #enabled(Serializer.EncoderContext)} once, when it is created, and writes
 * with {@link #encodeDouble(Encoder, double)} where the answer is true.
 *
 * @since 3.3.0
 */
@Internal
@UsedByGeneratedCode
public final class GeneratedSerdeWholeFloatsUtil {

    /**
     * Every whole number of this magnitude or less is a {@code double}. It is {@code Number.MAX_SAFE_INTEGER + 1}
     * of JavaScript.
     */
    private static final long MAX_WHOLE = 1L << 53;

    private static final Set<SerializationConfiguration.Feature> FEATURE =
        Set.of(SerializationConfiguration.Feature.WRITE_WHOLE_FLOATS_AS_INTEGERS);

    private GeneratedSerdeWholeFloatsUtil() {
    }

    /**
     * @param context The encoder context
     * @return Whether whole floating-point values are written as integers
     */
    public static boolean enabled(Serializer.EncoderContext context) {
        return context.getFeatures().contains(SerializationConfiguration.Feature.WRITE_WHOLE_FLOATS_AS_INTEGERS);
    }

    /**
     * The context of a bean that enables the feature, for the serializers of its properties.
     *
     * @param context The encoder context
     * @return The context with the feature
     */
    public static Serializer.EncoderContext withFeature(Serializer.EncoderContext context) {
        return context.withFeatures(FEATURE, Set.of());
    }

    /**
     * The context of a bean that disables the feature, for the serializers of its properties.
     *
     * @param context The encoder context
     * @return The context without the feature
     */
    public static Serializer.EncoderContext withoutFeature(Serializer.EncoderContext context) {
        return context.withFeatures(Set.of(), FEATURE);
    }

    /**
     * Writes a whole value of magnitude at most 2<sup>53</sup> as an integer and any other value as it is.
     * The comparison is false for {@code NaN}, an infinity and a fraction; negative zero is written as {@code 0}.
     *
     * @param encoder The encoder
     * @param value   The value
     * @throws IOException If an I/O error occurs
     */
    public static void encodeDouble(Encoder encoder, double value) throws IOException {
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
    public static void encodeFloat(Encoder encoder, float value) throws IOException {
        long whole = (long) value;
        if (whole == value && whole >= -MAX_WHOLE && whole <= MAX_WHOLE) {
            encoder.encodeLong(whole);
        } else {
            encoder.encodeFloat(value);
        }
    }
}
