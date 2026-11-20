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
package io.micronaut.serde.jackson;

import io.micronaut.core.annotation.Internal;

import java.util.Arrays;
import java.util.List;

/**
 * Matches the characters of a string value against a set of constant values without creating a
 * {@link String}.
 */
@Internal
final class StringValueMatcher {

    private final char[][] values;
    /**
     * Open addressing table of value indexes plus one, zero marks an empty slot.
     */
    private final int[] table;
    private final int mask;

    StringValueMatcher(List<String> values) {
        this.values = new char[values.size()][];
        int size = Integer.highestOneBit(Math.max(1, values.size() * 2 - 1)) << 1;
        this.table = new int[size];
        this.mask = size - 1;
        for (int i = 0; i < values.size(); i++) {
            char[] chars = values.get(i).toCharArray();
            this.values[i] = chars;
            if (match(chars, 0, chars.length) < 0) {
                int slot = hash(chars, 0, chars.length) & mask;
                while (table[slot] != 0) {
                    slot = (slot + 1) & mask;
                }
                table[slot] = i + 1;
            }
        }
    }

    /**
     * @param chars The characters
     * @param offset The offset of the value
     * @param length The length of the value
     * @return The index of the first matching value or -1
     */
    int match(char[] chars, int offset, int length) {
        int slot = hash(chars, offset, length) & mask;
        int entry;
        while ((entry = table[slot]) != 0) {
            char[] value = values[entry - 1];
            if (value.length == length && Arrays.equals(value, 0, length, chars, offset, offset + length)) {
                return entry - 1;
            }
            slot = (slot + 1) & mask;
        }
        return -1;
    }

    private static int hash(char[] chars, int offset, int length) {
        int h = 0;
        for (int i = offset, end = offset + length; i < end; i++) {
            h = 31 * h + chars[i];
        }
        return h ^ (h >>> 16);
    }
}
