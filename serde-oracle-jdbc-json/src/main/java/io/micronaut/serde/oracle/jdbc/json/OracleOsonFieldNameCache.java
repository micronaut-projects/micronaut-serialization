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

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Interns OSON field names across documents, so that a stream of documents with the same shape (the rows of a
 * query) allocates no name strings after the first. Names are keyed by a hash of their UTF-8 bytes and their
 * length; a collision falls back to decoding the name without caching it. The cache stops growing at a fixed
 * size so that documents with unbounded dynamic keys cannot leak.
 *
 * @since 3.3.0
 */
@Internal
public final class OracleOsonFieldNameCache {

    private static final int MAX_SIZE = 4096;
    private static final long SEED = 1125899906842597L;

    private final Map<Long, String> names = new ConcurrentHashMap<>();

    /**
     * Resolves a field name from the bytes of a document.
     *
     * @param data   the document
     * @param offset the offset of the name
     * @param length the length of the name in bytes
     * @return the name, shared with earlier documents where possible
     */
    String resolve(byte[] data, int offset, int length) {
        long hash = SEED;
        for (int i = 0; i < length; i++) {
            hash = 31 * hash + data[offset + i];
        }
        Long key = hash ^ ((long) length << 56);
        String name = names.get(key);
        if (name == null) {
            name = new String(data, offset, length, StandardCharsets.UTF_8);
            if (names.size() < MAX_SIZE) {
                names.putIfAbsent(key, name);
            }
            return name;
        }
        if (!matches(name, data, offset, length)) {
            // a hash collision between different names: do not cache the second
            return new String(data, offset, length, StandardCharsets.UTF_8);
        }
        return name;
    }

    private static boolean matches(String name, byte[] data, int offset, int length) {
        int n = name.length();
        if (n > length) {
            return false;
        }
        // fast path for ASCII names, which is what field names nearly always are
        for (int i = 0; i < n; i++) {
            char c = name.charAt(i);
            if (c >= 0x80) {
                return name.equals(new String(data, offset, length, StandardCharsets.UTF_8));
            }
            if (i >= length || data[offset + i] != c) {
                return false;
            }
        }
        return n == length;
    }
}
