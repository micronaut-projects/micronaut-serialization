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
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Keys;
import io.micronaut.serde.KeysAwareEncoder;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.util.BinaryCodecUtil;
import org.apache.fory.json.writer.Utf8JsonWriter;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;

/**
 * {@link Encoder} driving a Fory {@link Utf8JsonWriter}. A single instance tracks nesting with
 * a frame stack instead of allocating child encoders.
 */
@Internal
final class ForyEncoder extends LimitingStream implements KeysAwareEncoder {
    private final Utf8JsonWriter writer;
    private @Nullable Keys currentKeys;
    private ForyKeysProvider.@Nullable ForyKeys currentForyKeys;
    private int[] counts = new int[16];
    private boolean[] objectFrames = new boolean[16];
    private int depth;
    private boolean afterKey;
    private boolean pendingObjectStart;

    ForyEncoder(Utf8JsonWriter writer) {
        super(DEFAULT_LIMITS);
        this.writer = writer;
    }

    private void beforeValue() {
        if (afterKey) {
            afterKey = false;
        } else {
            if (pendingObjectStart) {
                flushObjectStart();
            }
            if (depth > 0) {
                writer.writeComma(counts[depth]++);
            }
        }
    }

    /**
     * Writes the deferred '{' of the current object, when the first key is not a packed {@link Keys} token.
     */
    private void flushObjectStart() {
        pendingObjectStart = false;
        writer.writeObjectStart();
    }

    private void push(boolean object) throws IOException {
        increaseDepth();
        if (++depth == counts.length) {
            counts = Arrays.copyOf(counts, depth * 2);
            objectFrames = Arrays.copyOf(objectFrames, depth * 2);
        }
        counts[depth] = 0;
        objectFrames[depth] = object;
    }

    @Override
    public Encoder encodeArray(Argument<?> type) throws IOException {
        beforeValue();
        writer.writeArrayStart();
        push(false);
        return this;
    }

    @Override
    public Encoder encodeObject(Argument<?> type) throws IOException {
        beforeValue();
        // '{' is deferred so the first key can be written together with it in one store
        pendingObjectStart = true;
        push(true);
        return this;
    }

    @Override
    public void finishStructure() throws IOException {
        if (depth == 0) {
            throw new IllegalStateException("Not a structure");
        }
        if (pendingObjectStart) {
            flushObjectStart();
        }
        if (objectFrames[depth--]) {
            writer.writeObjectEnd();
        } else {
            writer.writeArrayEnd();
        }
        decreaseDepth();
    }

    @Override
    public void encodeKey(Keys keys, int index) throws IOException {
        ForyKeysProvider.ForyKeys foryKeys = currentForyKeys;
        if (keys != currentKeys || foryKeys == null) {
            foryKeys = ForyKeysProvider.get(keys);
            currentKeys = keys;
            currentForyKeys = foryKeys;
        }
        int count = counts[depth]++;
        if (pendingObjectStart) {
            if (foryKeys.packed[index]) {
                pendingObjectStart = false;
                writer.writeObjectStartWithRawValue(foryKeys.brace0[index], foryKeys.brace1[index], foryKeys.commaLength[index]);
                afterKey = true;
                return;
            }
            flushObjectStart();
        }
        if (foryKeys.packed[index]) {
            // one or two word stores, comma variant selected by the element count
            writer.writeRawValue(foryKeys.name0[index], foryKeys.name1[index], foryKeys.comma0[index], foryKeys.comma1[index],
                foryKeys.nameLength[index], foryKeys.commaLength[index], count);
        } else {
            writer.writeComma(count);
            writer.writeRawValue(foryKeys.nameBytes[index]);
        }
        afterKey = true;
    }

    @Override
    public void encodeKey(String key) throws IOException {
        if (pendingObjectStart) {
            flushObjectStart();
        }
        writer.writeComma(counts[depth]++);
        writer.writeFieldName(key);
        afterKey = true;
    }

    @Override
    public void encodeString(String value) throws IOException {
        beforeValue();
        writer.writeString(value);
    }

    @Override
    public void encodeBoolean(boolean value) throws IOException {
        beforeValue();
        writer.writeBoolean(value);
    }

    @Override
    public void encodeByte(byte value) throws IOException {
        beforeValue();
        writer.writeInt(value);
    }

    @Override
    public void encodeShort(short value) throws IOException {
        beforeValue();
        writer.writeInt(value);
    }

    @Override
    public void encodeChar(char value) throws IOException {
        beforeValue();
        writer.writeInt(value);
    }

    @Override
    public void encodeInt(int value) throws IOException {
        beforeValue();
        writer.writeInt(value);
    }

    @Override
    public void encodeLong(long value) throws IOException {
        beforeValue();
        writer.writeLong(value);
    }

    @Override
    public void encodeFloat(float value) throws IOException {
        beforeValue();
        writer.writeFloat(value);
    }

    @Override
    public void encodeDouble(double value) throws IOException {
        beforeValue();
        writer.writeDouble(value);
    }

    @Override
    public void encodeBigInteger(BigInteger value) throws IOException {
        beforeValue();
        writer.writeBigInteger(value);
    }

    @Override
    public void encodeBigDecimal(BigDecimal value) throws IOException {
        beforeValue();
        writer.writeBigDecimal(value);
    }

    @Override
    public void encodeBinary(byte[] data) throws IOException {
        BinaryCodecUtil.encodeToBase64String(this, data);
    }

    @Override
    public void encodeNull() throws IOException {
        beforeValue();
        writer.writeNull();
    }

    @Override
    public String currentPath() {
        return "";
    }
}
