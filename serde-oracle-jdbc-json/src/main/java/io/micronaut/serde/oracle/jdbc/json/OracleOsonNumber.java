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
package io.micronaut.serde.oracle.jdbc.json;

import io.micronaut.core.annotation.Internal;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Decoder for Oracle's NUMBER encoding as it appears inside OSON documents. The first byte is the base 100
 * exponent (biased by 193, bit 7 set for positive values, complemented for negative ones), followed by base
 * 100 mantissa digits (each plus 1 for positive values, 101 minus the digit for negative ones) and a trailing
 * 102 on negative values.
 * <p>
 * The format description comes from the published open source drivers python-oracledb (Apache / UPL) and
 * rust-oracledb; nothing here was derived from the Oracle JDBC driver.
 *
 * @since 3.3.0
 */
@Internal
final class OracleOsonNumber {

    private static final int MAX_LONG_PAIRS = 9;

    private OracleOsonNumber() {
    }

    /**
     * Decodes a NUMBER to a long. Values with a fractional part are truncated, values with more than 18
     * integer digits go through {@link BigDecimal}.
     *
     * @param data   the document
     * @param offset the offset of the first byte
     * @param length the number of bytes
     * @return the value
     */
    static long toLong(byte[] data, int offset, int length) {
        int first = data[offset] & 0xFF;
        boolean positive = (first & 0x80) != 0;
        int exponent = exponent(first, positive);
        int mantissa = mantissaLength(data, offset, length, positive);
        if (mantissa == 0) {
            return 0;
        }
        // number of base 100 digits before the decimal point
        int integerPairs = exponent + 1;
        if (integerPairs < mantissa || integerPairs > MAX_LONG_PAIRS) {
            return toBigDecimal(data, offset, length).longValue();
        }
        long value = 0;
        for (int i = 0; i < mantissa; i++) {
            value = value * 100 + digit(data[offset + 1 + i] & 0xFF, positive);
        }
        for (int i = mantissa; i < integerPairs; i++) {
            value *= 100;
        }
        return positive ? value : -value;
    }

    /**
     * @param data   the document
     * @param offset the offset of the first byte
     * @param length the number of bytes
     * @return whether the value has no fractional part
     */
    static boolean isIntegral(byte[] data, int offset, int length) {
        int first = data[offset] & 0xFF;
        boolean positive = (first & 0x80) != 0;
        int mantissa = mantissaLength(data, offset, length, positive);
        return mantissa == 0 || mantissa <= exponent(first, positive) + 1;
    }

    /**
     * @param data   the document
     * @param offset the offset of the first byte
     * @param length the number of bytes
     * @return the value as a decimal
     */
    static BigDecimal toBigDecimal(byte[] data, int offset, int length) {
        int first = data[offset] & 0xFF;
        boolean positive = (first & 0x80) != 0;
        int exponent = exponent(first, positive);
        int mantissa = mantissaLength(data, offset, length, positive);
        if (mantissa == 0) {
            return BigDecimal.ZERO;
        }
        BigInteger unscaled;
        if (mantissa <= MAX_LONG_PAIRS) {
            long value = 0;
            for (int i = 0; i < mantissa; i++) {
                value = value * 100 + digit(data[offset + 1 + i] & 0xFF, positive);
            }
            unscaled = BigInteger.valueOf(value);
        } else {
            unscaled = BigInteger.ZERO;
            for (int i = 0; i < mantissa; i++) {
                unscaled = unscaled.multiply(BigInteger.valueOf(100)).add(BigInteger.valueOf(digit(data[offset + 1 + i] & 0xFF, positive)));
            }
        }
        // mantissa pairs cover exponent+1 integer pairs; scale is in decimal digits
        int scale = (mantissa - (exponent + 1)) * 2;
        BigDecimal result = new BigDecimal(positive ? unscaled : unscaled.negate(), scale);
        return scale > 0 ? result.stripTrailingZeros() : result;
    }

    /**
     * @param data   the document
     * @param offset the offset of the first byte
     * @param length the number of bytes
     * @return the value as a double
     */
    static double toDouble(byte[] data, int offset, int length) {
        return toBigDecimal(data, offset, length).doubleValue();
    }

    /**
     * @param data   the document
     * @param offset the offset of the first byte
     * @param length the number of bytes
     * @return the value as a plain decimal string
     */
    static String toPlainString(byte[] data, int offset, int length) {
        return toBigDecimal(data, offset, length).toPlainString();
    }

    private static int exponent(int first, boolean positive) {
        int exponentByte = positive ? first : (~first) & 0xFF;
        return (byte) (exponentByte - 193);
    }

    private static int mantissaLength(byte[] data, int offset, int length, boolean positive) {
        int mantissa = length - 1;
        if (!positive && mantissa > 0 && (data[offset + length - 1] & 0xFF) == 102) {
            mantissa--;
        }
        return mantissa;
    }

    private static int digit(int b, boolean positive) {
        return positive ? b - 1 : 101 - b;
    }
}
