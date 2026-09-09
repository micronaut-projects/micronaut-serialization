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
package io.micronaut.serde.toon.util;

import io.micronaut.core.annotation.Internal;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Formats numbers in the canonical decimal form required by the TOON
 * specification: no leading zeros, no trailing fractional zeros, {@code -0}
 * normalized to {@code 0}, {@code NaN}/infinite values encoded as
 * {@code null}, and scientific notation only outside the
 * {@code 1e-6 <= |n| < 1e21} magnitude window.
 *
 * @see <a href="https://github.com/toon-format/spec">TOON specification</a>
 * @since 3.2.0
 */
@Internal
public final class ToonNumbers {

    private static final BigDecimal MIN_PLAIN_MAGNITUDE = new BigDecimal("1e-6");
    private static final BigDecimal MAX_PLAIN_MAGNITUDE = new BigDecimal("1e21");

    private ToonNumbers() {
    }

    /**
     * Formats the given number in canonical TOON decimal form.
     *
     * @param number The number to format
     * @return The canonical decimal (or, outside the plain-form magnitude
     * window, scientific notation) representation, or {@code "null"} for a
     * {@code NaN} or infinite floating point value
     */
    public static String format(Number number) {
        switch (number) {
            case Double d -> {
                if (d.isNaN() || d.isInfinite()) {
                    return "null";
                }

                return canonicalDecimal(new BigDecimal(d.toString()));
            }

            case Float f -> {
                if (f.isNaN() || f.isInfinite()) {
                    return "null";
                }
                return canonicalDecimal(new BigDecimal(f.toString()));
            }

            case BigDecimal bd -> {
                return canonicalDecimal(bd);
            }

            case BigInteger bi -> {
                return canonicalDecimal(new BigDecimal(bi));
            }

            default -> {
            }
        }

        // Byte, Short, Integer, Long: always within the plain-form magnitude
        // window and never fractional, so the JDK's own decimal form is
        // already canonical.
        return number.toString();
    }

    private static String canonicalDecimal(BigDecimal value) {
        if (value.signum() == 0) {
            return "0";
        }

        BigDecimal stripped = value.stripTrailingZeros();
        BigDecimal magnitude = value.abs();
        if (magnitude.compareTo(MIN_PLAIN_MAGNITUDE) >= 0 && magnitude.compareTo(MAX_PLAIN_MAGNITUDE) < 0) {
            return stripped.toPlainString();
        }

        return scientific(stripped);
    }

    /**
     * Formats an already trailing-zero-stripped decimal in scientific
     * notation, e.g. {@code 1e+23} or {@code 1e-10}.
     *
     * @param stripped The value to format, with trailing zeros already stripped
     * @return The value in {@code <mantissa>e<signed exponent>} form
     */
    private static String scientific(BigDecimal stripped) {
        BigInteger unscaled = stripped.unscaledValue();

        boolean negative = unscaled.signum() < 0;
        String digits = unscaled.abs().toString();

        int exponent = digits.length() - 1 - stripped.scale();
        String mantissa = digits.length() > 1 ? digits.charAt(0) + "." + digits.substring(1) : digits;

        if (mantissa.indexOf('.') >= 0) {
            mantissa = mantissa.replaceAll("0+$", "");
            mantissa = mantissa.replaceAll("\\.$", "");
        }

        return (negative ? "-" : "") + mantissa + "e" + (exponent >= 0 ? "+" : "") + exponent;
    }
}
