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

import java.util.regex.Pattern;

/**
 * Quoting and escaping rules shared by {@link ToonWriter} (encode) and
 * {@code ToonTreeAdapter} (decode).
 *
 * @see <a href="https://github.com/toon-format/spec">TOON specification</a>
 * @since 3.2.0
 */
@Internal
public final class ToonEscapes {

    private static final Pattern NUMERIC_LITERAL = Pattern.compile("^[+-]?[0-9]+(?:\\.[0-9]+)?(?:e[+-]?[0-9]+)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNQUOTED_KEY = Pattern.compile("^[A-Za-z_][A-Za-z0-9_.]*$");
    private static final char CONTROL_CHARACTER_BOUNDARY = 0x20;

    private ToonEscapes() {
    }

    /**
     * Quotes and escapes the given string value if the TOON specification
     * requires it in the context of the given active delimiter, otherwise
     * returns it unchanged.
     *
     * @param value     The unquoted string value
     * @param delimiter The active delimiter for the current context
     * @return The value, quoted and escaped if required
     */
    public static String quoteValueIfNeeded(String value, char delimiter) {
        return needsQuoting(value, delimiter) ? quote(value) : value;
    }

    /**
     * Quotes and escapes the given key if it does not match the unquoted key
     * grammar ({@code ^[A-Za-z_][A-Za-z0-9_.]*$}), otherwise returns it
     * unchanged.
     *
     * @param key The unquoted key
     * @return The key, quoted and escaped if required
     */
    public static String quoteKeyIfNeeded(String key) {
        return UNQUOTED_KEY.matcher(key).matches() ? key : quote(key);
    }

    private static boolean needsQuoting(String value, char delimiter) {
        if (value.isEmpty()) {
            return true;
        }

        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if (isSpaceOrTab(first) || isSpaceOrTab(last)) {
            return true;
        }

        if (value.equals("true") || value.equals("false") || value.equals("null")) {
            return true;
        }

        if (first == '-' || first == '#') {
            return true;
        }

        if (NUMERIC_LITERAL.matcher(value).matches()) {
            return true;
        }

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == delimiter || c == ':' || c == '"' || c == '\\' || c == '[' || c == ']' || c == '{' || c == '}' || c < CONTROL_CHARACTER_BOUNDARY) {
                return true;
            }
        }

        return false;
    }

    private static boolean isSpaceOrTab(char c) {
        return c == ' ' || c == '\t';
    }

    private static String quote(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    // Convert non-printable characters to their Unicode escape sequences.
                    if (c < CONTROL_CHARACTER_BOUNDARY) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }

        sb.append('"');
        return sb.toString();
    }
}
