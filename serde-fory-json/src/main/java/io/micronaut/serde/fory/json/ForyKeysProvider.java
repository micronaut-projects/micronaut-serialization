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
package io.micronaut.serde.fory.json;

import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.Keys;
import io.micronaut.serde.KeysProvider;
import io.micronaut.serde.KeysSupport;
import org.apache.fory.json.ForyJson;
import org.apache.fory.json.ForyJsonAccess;
import org.apache.fory.json.JsonConfig;
import org.apache.fory.json.resolver.JsonSharedRegistry;
import org.apache.fory.json.resolver.JsonTypeResolver;
import org.apache.fory.json.writer.Utf8JsonWriter;

import java.util.List;

/**
 * Contributes precomputed Fory field-name tokens to {@link Keys}: the escaped UTF-8 bytes of
 * {@code "name":}, {@code ,"name":} and {@code {"name":}, packed into little-endian {@code long} words the same way
 * Fory's generated codecs embed them as constants.
 *
 * @author Denis Stepanov
 * @since 3.1
 */
@Internal
public final class ForyKeysProvider implements KeysProvider {

    @Override
    public Class<?> keysType() {
        return ForyKeysProvider.class;
    }

    @Override
    public Object[] create(List<String> keys) {
        return new Object[]{new ForyKeys(keys)};
    }

    static ForyKeys get(Keys keys) {
        return (ForyKeys) KeysSupport.get(keys, IndexHolder.KEYS_INDEX)[0];
    }

    /**
     * Per-key tokens. Writer tokens up to 16 bytes are packed into two words; longer ones keep the
     * raw bytes. Reader tokens up to 16 bytes are matched with one or two masked word compares.
     */
    static final class ForyKeys {
        private static final int READ_NONE = 0;
        private static final int READ_ONE_WORD = 1;
        private static final int READ_TWO_WORDS = 2;

        final boolean[] packed;
        final long[] name0;
        final long[] name1;
        final long[] comma0;
        final long[] comma1;
        final long[] brace0;
        final long[] brace1;
        final int[] nameLength;
        final int[] commaLength;
        final byte[][] nameBytes;
        final int[] readKind;
        final long[] readWord;
        final long[] readSuffix;
        final long[] readMask;

        ForyKeys(List<String> keys) {
            int size = keys.size();
            packed = new boolean[size];
            name0 = new long[size];
            name1 = new long[size];
            comma0 = new long[size];
            comma1 = new long[size];
            brace0 = new long[size];
            brace1 = new long[size];
            nameLength = new int[size];
            commaLength = new int[size];
            nameBytes = new byte[size][];
            readKind = new int[size];
            readWord = new long[size];
            readSuffix = new long[size];
            readMask = new long[size];
            Utf8JsonWriter writer = new Utf8JsonWriter(Holder.CONFIG, Holder.RESOLVER, new byte[64]);
            for (int i = 0; i < size; i++) {
                writer.reset();
                writer.writeFieldName(keys.get(i));
                byte[] name = writer.toJsonBytes();
                byte[] comma = new byte[name.length + 1];
                comma[0] = ',';
                System.arraycopy(name, 0, comma, 1, name.length);
                nameBytes[i] = name;
                nameLength[i] = name.length;
                commaLength[i] = comma.length;
                if (comma.length <= 2 * Long.BYTES) {
                    packed[i] = true;
                    name0[i] = word(name, 0);
                    name1[i] = word(name, Long.BYTES);
                    comma0[i] = word(comma, 0);
                    comma1[i] = word(comma, Long.BYTES);
                    comma[0] = (byte) '{';
                    brace0[i] = word(comma, 0);
                    brace1[i] = word(comma, Long.BYTES);
                }
                if (name.length <= Long.BYTES) {
                    readKind[i] = READ_ONE_WORD;
                    readWord[i] = word(name, 0);
                    readMask[i] = mask(name.length);
                } else if (name.length <= 2 * Long.BYTES) {
                    readKind[i] = READ_TWO_WORDS;
                    readWord[i] = word(name, 0);
                    readSuffix[i] = word(name, Long.BYTES);
                    readMask[i] = mask(name.length - Long.BYTES);
                } else {
                    readKind[i] = READ_NONE;
                }
            }
        }

        boolean canMatch(int index) {
            return readKind[index] != READ_NONE;
        }

        boolean isOneWord(int index) {
            return readKind[index] == READ_ONE_WORD;
        }

        private static long word(byte[] bytes, int offset) {
            long word = 0;
            int end = Math.min(bytes.length, offset + Long.BYTES);
            for (int i = end - 1; i >= offset; i--) {
                word = (word << 8) | (bytes[i] & 0xFF);
            }
            return word;
        }

        private static long mask(int length) {
            return length >= Long.BYTES ? -1L : (1L << (length * 8)) - 1;
        }
    }

    private static final class IndexHolder {
        static final int KEYS_INDEX = KeysSupport.indexOf(new ForyKeysProvider());
    }

    private static final class Holder {
        static final JsonConfig CONFIG = ForyJsonAccess.config(ForyJson.builder().build());
        static final JsonTypeResolver RESOLVER = new JsonTypeResolver(new JsonSharedRegistry(CONFIG));
    }
}
