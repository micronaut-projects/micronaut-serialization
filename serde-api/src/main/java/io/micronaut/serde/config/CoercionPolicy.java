/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.serde.config;

import io.micronaut.serde.Decoder;
import org.jspecify.annotations.Nullable;

/**
 * The scalar coercions a {@link Decoder} is allowed to perform, resolved once from the
 * {@link DeserializationConfiguration} and then passed to every decoder that is created, in the
 * same way as {@link io.micronaut.serde.LimitingStream.RemainingLimits}.
 * <p>
 * A decoder that is handed a policy must consult it at every point where it reads a value whose
 * JSON shape does not match the requested type, and must pass its own policy on to the decoders it
 * creates, in particular in {@link Decoder#decodeBuffer()}, so that the same input is accepted or
 * rejected no matter which decoder ends up reading it.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
public final class CoercionPolicy {

    /**
     * All coercions allowed. This is the historical behaviour and the default.
     */
    public static final CoercionPolicy LENIENT = new CoercionPolicy(allMask());

    /**
     * No coercions allowed, values must have the shape of the target type.
     */
    public static final CoercionPolicy STRICT = new CoercionPolicy(0);

    private final int allowed;
    private final int[] allowedShapes;

    private CoercionPolicy(int allowed) {
        this.allowed = allowed;
        this.allowedShapes = precalculate(allowed);
    }

    /**
     * Turn the configured coercions into one bit set of acceptable {@link Shape}s per
     * {@link Target}, so that a decoder can reduce every check to a single mask test instead of
     * re-deriving which coercion a token would need.
     */
    private static int[] precalculate(int allowed) {
        int[] shapes = new int[Target.VALUES.length];
        for (Target target : Target.VALUES) {
            int mask = bit(Shape.OTHER);
            for (Shape shape : Shape.VALUES) {
                if (shape == Shape.OTHER) {
                    continue;
                }
                Coercion coercion = coercion(target, shape);
                if (coercion == null || (allowed & coercion.mask) != 0) {
                    mask |= bit(shape);
                }
            }
            shapes[target.ordinal()] = mask;
        }
        return shapes;
    }

    /**
     * The coercion needed to read a value of the given shape as the given target type, or
     * {@code null} if the shape is the natural one for that target and no coercion is involved.
     *
     * @param target The target type
     * @param shape  The shape of the value in the document
     * @return The coercion, or {@code null} if none is needed
     */
    @Nullable
    public static Coercion coercion(Target target, Shape shape) {
        if (shape == Shape.ARRAY) {
            return Coercion.UNWRAP_SINGLE_VALUE_ARRAY;
        }
        return switch (target) {
            case INTEGER -> switch (shape) {
                case FLOAT_NUMBER -> Coercion.FLOAT_AS_INT;
                case STRING -> Coercion.STRING_AS_NUMBER;
                case BOOLEAN -> Coercion.BOOLEAN_AS_NUMBER;
                default -> null;
            };
            case DECIMAL -> switch (shape) {
                case STRING -> Coercion.STRING_AS_NUMBER;
                case BOOLEAN -> Coercion.BOOLEAN_AS_NUMBER;
                default -> null;
            };
            case BOOLEAN -> switch (shape) {
                case INTEGER_NUMBER, FLOAT_NUMBER -> Coercion.NUMBER_AS_BOOLEAN;
                case STRING -> Coercion.STRING_AS_BOOLEAN;
                default -> null;
            };
            case STRING -> switch (shape) {
                case INTEGER_NUMBER, FLOAT_NUMBER, BOOLEAN -> Coercion.SCALAR_AS_STRING;
                default -> null;
            };
            // a single character string is the natural shape of a char, and an integer is its code point
            case CHAR -> switch (shape) {
                case FLOAT_NUMBER -> Coercion.FLOAT_AS_INT;
                case BOOLEAN -> Coercion.BOOLEAN_AS_NUMBER;
                default -> null;
            };
        };
    }

    /**
     * The precalculated bit set of {@link Shape}s that may be read as the given target type. A
     * decoder should read this once, when it is created, and test it with
     * {@link Shape#bit()} for every value it reads.
     *
     * @param target The target type
     * @return The bit set of acceptable shapes
     */
    public int allowedShapes(Target target) {
        return allowedShapes[target.ordinal()];
    }

    private static int bit(Shape shape) {
        return 1 << shape.ordinal();
    }

    /**
     * Resolve the policy from the given configuration.
     *
     * @param configuration The deserialization configuration, may be {@code null}
     * @return The coercion policy, {@link #LENIENT} if there is no configuration
     */
    public static CoercionPolicy fromConfiguration(@Nullable DeserializationConfiguration configuration) {
        if (configuration == null) {
            return LENIENT;
        }
        int allowed = 0;
        allowed |= mask(Coercion.FLOAT_AS_INT, configuration.isAcceptFloatAsInt());
        allowed |= mask(Coercion.STRING_AS_NUMBER, configuration.isAcceptStringAsNumber());
        allowed |= mask(Coercion.BOOLEAN_AS_NUMBER, configuration.isAcceptBooleanAsNumber());
        allowed |= mask(Coercion.NUMBER_AS_BOOLEAN, configuration.isAcceptNumberAsBoolean());
        allowed |= mask(Coercion.STRING_AS_BOOLEAN, configuration.isAcceptStringAsBoolean());
        allowed |= mask(Coercion.SCALAR_AS_STRING, configuration.isAcceptScalarAsString());
        allowed |= mask(Coercion.UNWRAP_SINGLE_VALUE_ARRAY, configuration.isUnwrapSingleValueArrays());
        if (allowed == LENIENT.allowed) {
            return LENIENT;
        }
        if (allowed == 0) {
            return STRICT;
        }
        return new CoercionPolicy(allowed);
    }

    /**
     * @param coercion The coercion
     * @return Whether the decoder may perform it
     */
    public boolean isAllowed(Coercion coercion) {
        return (allowed & coercion.mask) != 0;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CoercionPolicy other && other.allowed == allowed;
    }

    @Override
    public int hashCode() {
        return allowed;
    }

    @Override
    public String toString() {
        if (allowed == LENIENT.allowed) {
            return "CoercionPolicy.LENIENT";
        }
        if (allowed == 0) {
            return "CoercionPolicy.STRICT";
        }
        StringBuilder builder = new StringBuilder("CoercionPolicy[");
        boolean first = true;
        for (Coercion coercion : Coercion.VALUES) {
            if (isAllowed(coercion)) {
                if (!first) {
                    builder.append(", ");
                }
                builder.append(coercion.name());
                first = false;
            }
        }
        return builder.append(']').toString();
    }

    /**
     * The shape of a value in the document, as far as coercion is concerned.
     */
    public enum Shape {
        /**
         * A string.
         */
        STRING,
        /**
         * A number without a fractional part.
         */
        INTEGER_NUMBER,
        /**
         * A number with a fractional part.
         */
        FLOAT_NUMBER,
        /**
         * A boolean.
         */
        BOOLEAN,
        /**
         * An array, which a decoder may unwrap if it holds a single value.
         */
        ARRAY,
        /**
         * Anything else: null, objects, structural tokens, format specific values. These are never
         * coerced, the decoder rejects or handles them on its own.
         */
        OTHER;

        static final Shape[] VALUES = values();

        private final int bit = 1 << ordinal();

        /**
         * @return This shape as a bit, to test against {@link CoercionPolicy#allowedShapes(Target)}
         */
        public int bit() {
            return bit;
        }
    }

    /**
     * The type a decoder is reading a value as.
     */
    public enum Target {
        /**
         * {@code byte}, {@code short}, {@code int}, {@code long} or {@link java.math.BigInteger}.
         */
        INTEGER,
        /**
         * {@code float}, {@code double} or {@link java.math.BigDecimal}.
         */
        DECIMAL,
        /**
         * {@code boolean}.
         */
        BOOLEAN,
        /**
         * {@link String}.
         */
        STRING,
        /**
         * {@code char}.
         */
        CHAR;

        static final Target[] VALUES = values();
    }

    private static int mask(Coercion coercion, boolean allowed) {
        return allowed ? coercion.mask : 0;
    }

    private static int allMask() {
        int mask = 0;
        for (Coercion coercion : Coercion.values()) {
            mask |= coercion.mask;
        }
        return mask;
    }

    /**
     * A single coercion a decoder may perform when the JSON shape does not match the requested
     * type.
     */
    public enum Coercion {
        /**
         * Read a floating point number into an integer type, truncating it. {@code 42.5} becomes
         * {@code 42}.
         */
        FLOAT_AS_INT("Cannot coerce a floating point value to an integer"),
        /**
         * Read a string into a numeric type. {@code "42"} becomes {@code 42}.
         */
        STRING_AS_NUMBER("Cannot coerce a string to a number"),
        /**
         * Read a boolean into a numeric type. {@code true} becomes {@code 1}.
         */
        BOOLEAN_AS_NUMBER("Cannot coerce a boolean to a number"),
        /**
         * Read a number into a boolean. Any non-zero value becomes {@code true}.
         */
        NUMBER_AS_BOOLEAN("Cannot coerce a number to a boolean"),
        /**
         * Read a string into a boolean. {@code "true"} becomes {@code true}, anything else
         * {@code false}.
         */
        STRING_AS_BOOLEAN("Cannot coerce a string to a boolean"),
        /**
         * Read a number or boolean into a string. {@code 1234} becomes {@code "1234"}.
         */
        SCALAR_AS_STRING("Cannot coerce a non-string value to a string"),
        /**
         * Read a single element array into the scalar it contains. {@code [42]} becomes
         * {@code 42}.
         */
        UNWRAP_SINGLE_VALUE_ARRAY("Cannot coerce a single element array to a single value");

        static final Coercion[] VALUES = values();

        final int mask;
        private final String message;

        Coercion(String message) {
            this.mask = 1 << ordinal();
            this.message = message;
        }

        /**
         * @return The message to report when this coercion is not allowed
         */
        public String message() {
            return message;
        }
    }
}
