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
 * Shared TOML text rendering helpers for output styles.
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

    private static boolean canUseLiteralString(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\'' || (c < 0x20 && c != '\t') || c == 0x7f) {
                return false;
            }
        }
        return true;
    }

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

    private static String unicodeEscape(int codePoint) {
        if (codePoint <= 0xffff) {
            return "\\u" + leftPad(Integer.toHexString(codePoint).toUpperCase(Locale.ROOT), 4);
        }
        return "\\U" + leftPad(Integer.toHexString(codePoint).toUpperCase(Locale.ROOT), 8);
    }

    private static String leftPad(String value, int length) {
        if (value.length() >= length) {
            return value;
        }
        return "0".repeat(length - value.length()) + value;
    }
}
