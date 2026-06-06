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
package io.micronaut.serde.properties.util;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/**
 * Container class for definitions of characters to escape.
 *
 * <p>Adapted from upstream {@code tools.jackson.dataformat.javaprop.io.JPropEscapes}.</p>
 *
 * @since 3.0.1
 */
@Internal
final class PropertiesEscapes {
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();
    private static final int UNICODE_ESCAPE = -1;
    private static final int[] VALUE_ESCAPES;
    private static final int[] KEY_ESCAPES;

    static {
        final int[] table = new int[256];
        for (int i = 0; i < 32; ++i) {
            table[i] = UNICODE_ESCAPE;
            table[128 + i] = UNICODE_ESCAPE;
        }
        table[0x7F] = UNICODE_ESCAPE;

        table['\t'] = 't';
        table['\r'] = 'r';
        table['\n'] = 'n';
        table['\\'] = '\\';
        VALUE_ESCAPES = table;
    }

    static {
        final int[] table = Arrays.copyOf(VALUE_ESCAPES, 256);
        table['#'] = '#';
        table['!'] = '!';
        table['='] = '=';
        table[':'] = ':';
        table[' '] = ' ';
        KEY_ESCAPES = table;
    }

    private PropertiesEscapes() {
    }

    static void appendKey(StringBuilder sb, String key) {
        final int end = key.length();
        if (end == 0) {
            return;
        }
        final int[] esc = KEY_ESCAPES;
        int i = 0;

        while (true) {
            char c = key.charAt(i);
            if ((c > 0xFF) || esc[c] != 0) {
                break;
            }
            sb.append(c);
            if (++i == end) {
                return;
            }
        }
        appendWithEscapes(sb, key, esc, i);
    }

    static @Nullable StringBuilder appendValue(String value) {
        final int end = value.length();
        if (end == 0) {
            return null;
        }
        final int[] esc = VALUE_ESCAPES;
        int i = 0;

        while (true) {
            char c = value.charAt(i);
            if ((c > 0xFF) || esc[c] != 0) {
                break;
            }
            if (++i == end) {
                return null;
            }
        }
        StringBuilder sb = new StringBuilder(end + 5 + (end >> 3));
        for (int j = 0; j < i; ++j) {
            sb.append(value.charAt(j));
        }
        appendWithEscapes(sb, value, esc, i);
        return sb;
    }

    private static void appendWithEscapes(StringBuilder sb, String key, int[] esc, int i) {
        final int end = key.length();
        do {
            char c = key.charAt(i);
            int type = (c > 0xFF) ? UNICODE_ESCAPE : esc[c];
            if (type == 0) {
                sb.append(c);
                continue;
            }
            if (type == UNICODE_ESCAPE) {
                sb.append('\\');
                sb.append('u');
                sb.append(HEX[c >>> 12]);
                sb.append(HEX[(c >> 8) & 0xF]);
                sb.append(HEX[(c >> 4) & 0xF]);
                sb.append(HEX[c & 0xF]);
            } else {
                sb.append('\\');
                sb.append((char) type);
            }
        } while (++i < end);
    }
}
