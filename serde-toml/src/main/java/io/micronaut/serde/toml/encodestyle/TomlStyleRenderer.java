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
package io.micronaut.serde.toml.encodestyle;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Shared TOML text rendering helpers used by both table and inline output styles.
 *
 * @see <a href="https://toml.io/en/v1.0.0#keys">TOML v1.0.0 Keys</a>
 * @see <a href="https://toml.io/en/v1.0.0#string">TOML v1.0.0 String</a>
 */
final class TomlStyleRenderer {
    /**
     * Reference to the <a href="https://toml.io/en/v1.0.0#keys">TOML v1.0.0 keys specification</a>.
     */
    private static final Pattern BARE_KEY = Pattern.compile("[A-Za-z0-9_-]+");

    private TomlStyleRenderer() {
    }

    static String renderKeySegment(String key) {
        if (BARE_KEY.matcher(key).matches()) {
            return key;
        }
        return renderString(key);
    }

    /**
     * Reference to the <a href="https://toml.io/en/v1.0.0#string">TOML v1.0.0 String specification</a>.
     */
    static String renderString(String value) {
        if (canUseLiteralString(value)) {
            return "'" + value + "'";
        }
        return "\"" + escapeBasicString(value) + "\"";
    }

    /**
     * Escapes a value for use inside a TOML basic string (double-quoted).
     * Handles the seven mandatory escape sequences ({@code \b \t \n \f \r \" \\})
     * and encodes remaining control characters as {@code &#92;uXXXX}.
     *
     * @see <a href="https://toml.io/en/v1.0.0#string">TOML v1.0.0 String — Basic String</a>
     * @param value
     * @return boolean
     */
    private static boolean canUseLiteralString(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\'' || (c < 0x20 && c != '\t') || c == 0x7f) {
                return false;
            }
        }
        return true;
    }

    /**
     * Produces a TOML unicode escape: {@code &#92;uXXXX} for BMP code points,
     * {@code &#92;UXXXXXXXX} for supplementary code points.
     *
     * @see <a href="https://toml.io/en/v1.0.0#string">TOML v1.0.0 String — Basic String</a>
     * @param value
     * @return String
     */
    private static String escapeBasicString(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length();) {
            int codePoint = value.codePointAt(i);
            switch (codePoint) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\t' -> builder.append("\\t");
                case '\n' -> builder.append("\\n");
                case '\f' -> builder.append("\\f");
                case '\r' -> builder.append("\\r");
                default -> {
                    if (codePoint < 0x20 || codePoint == 0x7f) {
                        builder.append(unicodeEscape(codePoint));
                    } else {
                        builder.appendCodePoint(codePoint);
                    }
                }
            }
            i += Character.charCount(codePoint);
        }
        return builder.toString();
    }

    /**
     * Produces a TOML unicode escape for control characters and supplementary code points.
     *
     * @see <a href="https://toml.io/en/v1.0.0#string">TOML v1.0.0 String — Basic String</a>
     */
    private static String unicodeEscape(int codePoint) {
        if (codePoint <= 0xffff) {
            return "\\u" + leftPad(Integer.toHexString(codePoint).toUpperCase(Locale.ROOT), 4);
        }
        return "\\U" + leftPad(Integer.toHexString(codePoint).toUpperCase(Locale.ROOT), 8);
    }

    /**
     * Left-pads a string with zeros to the specified length.
     */
    private static String leftPad(String value, int length) {
        if (value.length() >= length) {
            return value;
        }
        return "0".repeat(length - value.length()) + value;
    }
}
