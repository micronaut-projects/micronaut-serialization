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
package io.micronaut.serde.yaml;

import io.micronaut.core.annotation.Internal;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Parses numeric scalars of the YAML 1.2 core schema.
 *
 * <p>Integers may be decimal, octal ({@code 0o17}) or hexadecimal ({@code 0x1F}) and grow into a
 * {@link BigInteger} when they do not fit a {@code long}. Floats accept the YAML notation for
 * non-finite values ({@code .inf}, {@code -.inf}, {@code .nan}) next to the Java notation.</p>
 *
 * @since 3.2.0
 */
@Internal
final class YamlNumbers {

    /**
     * The largest exponent a decimal scalar may carry. A scalar such as {@code 1e200000000} is a
     * dozen characters long, so no input size limit bounds it, but expanding it to an unscaled
     * value claims hundreds of megabytes and minutes of CPU. The limit matches the one Jackson
     * applies to the same conversion.
     */
    static final int MAX_SCALE = 100_000;

    private YamlNumbers() {
    }

    /**
     * Parses the scalar into the narrowest {@link Number} that holds it exactly: an
     * {@link Integer}, a {@link Long} or a {@link BigInteger} for integers and a {@link Double}
     * for floats.
     *
     * @param text The scalar text
     * @return The number
     * @throws NumberFormatException if the text is not a core schema number
     */
    static Number parse(String text) {
        Number nonFinite = parseNonFinite(text);
        if (nonFinite != null) {
            return nonFinite;
        }
        BigInteger integer = parseInteger(text);
        if (integer != null) {
            if (integer.bitLength() < 32) {
                return integer.intValue();
            }
            if (integer.bitLength() < 64) {
                return integer.longValue();
            }
            return integer;
        }
        return Double.parseDouble(text);
    }

    /**
     * Parses the scalar into a {@link BigDecimal} without loss of precision.
     *
     * @param text The scalar text
     * @return The number
     * @throws NumberFormatException if the text is not a finite core schema number
     */
    static BigDecimal parseBigDecimal(String text) {
        BigInteger integer = parseInteger(text);
        if (integer != null) {
            return new BigDecimal(integer);
        }
        if (parseNonFinite(text) != null) {
            throw new NumberFormatException("Non-finite value " + text + " cannot be represented as a BigDecimal");
        }
        BigDecimal decimal = new BigDecimal(text);
        // Math.abs overflows on Integer.MIN_VALUE, compare against the bounds instead
        if (decimal.scale() > MAX_SCALE || decimal.scale() < -MAX_SCALE) {
            throw new NumberFormatException(
                "Value " + text + " has an exponent beyond the supported range of " + MAX_SCALE + " digits");
        }
        return decimal;
    }

    private static @org.jspecify.annotations.Nullable Number parseNonFinite(String text) {
        int start = 0;
        boolean negative = false;
        if (!text.isEmpty() && (text.charAt(0) == '-' || text.charAt(0) == '+')) {
            negative = text.charAt(0) == '-';
            start = 1;
        }
        if (text.length() - start != 4 || text.charAt(start) != '.') {
            return null;
        }
        String word = text.substring(start + 1);
        if (word.equals("inf") || word.equals("Inf") || word.equals("INF")) {
            return negative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        }
        if (start == 0 && (word.equals("nan") || word.equals("NaN") || word.equals("NAN"))) {
            return Double.NaN;
        }
        return null;
    }

    private static @org.jspecify.annotations.Nullable BigInteger parseInteger(String text) {
        int start = 0;
        boolean negative = false;
        if (!text.isEmpty() && (text.charAt(0) == '-' || text.charAt(0) == '+')) {
            negative = text.charAt(0) == '-';
            start = 1;
        }
        if (text.length() == start) {
            return null;
        }
        int radix = 10;
        if (text.length() - start > 2 && text.charAt(start) == '0') {
            char prefix = text.charAt(start + 1);
            if (prefix == 'x') {
                radix = 16;
                start += 2;
            } else if (prefix == 'o') {
                radix = 8;
                start += 2;
            }
        }
        for (int i = start; i < text.length(); i++) {
            if (Character.digit(text.charAt(i), radix) < 0) {
                return null;
            }
        }
        BigInteger value = new BigInteger(text.substring(start), radix);
        return negative ? value.negate() : value;
    }
}
