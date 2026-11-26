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
package io.micronaut.serde.bson;

import io.micronaut.core.annotation.Internal;
import org.bson.BsonSerializationException;
import org.bson.ByteBuf;
import org.bson.ByteBufNIO;
import org.bson.io.OutputBuffer;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;

/**
 * A BSON output backed by a growing byte array.
 *
 * <p>{@link org.bson.io.BasicOutputBuffer} writes every byte through a {@link ByteBuffer} with a
 * bounds check, and encodes strings code point by code point. This buffer writes into the array
 * directly and copies ASCII strings in one pass; other strings use the encoding of
 * {@link OutputBuffer}.</p>
 *
 * @since 3.3
 */
@Internal
final class BsonOutputBuffer extends OutputBuffer {

    static final int INITIAL_SIZE = 1024;

    private static final VarHandle INT_HANDLE = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle LONG_HANDLE = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

    private byte[] buffer;
    private int position;

    BsonOutputBuffer() {
        this(INITIAL_SIZE);
    }

    /**
     * @param initialSize The initial size of the buffer
     */
    BsonOutputBuffer(int initialSize) {
        buffer = new byte[initialSize];
    }

    @Override
    public void writeBytes(byte[] bytes, int offset, int length) {
        ensure(length);
        System.arraycopy(bytes, offset, buffer, position, length);
        position += length;
    }

    @Override
    public void writeByte(int value) {
        ensure(1);
        buffer[position++] = (byte) value;
    }

    @Override
    protected void write(int absolutePosition, int value) {
        checkPosition(absolutePosition, 1);
        buffer[absolutePosition] = (byte) value;
    }

    @Override
    public void writeInt32(int value) {
        ensure(4);
        INT_HANDLE.set(buffer, position, value);
        position += 4;
    }

    @Override
    public void writeInt32(int absolutePosition, int value) {
        checkPosition(absolutePosition, 4);
        INT_HANDLE.set(buffer, absolutePosition, value);
    }

    @Override
    public void writeInt64(long value) {
        ensure(8);
        LONG_HANDLE.set(buffer, position, value);
        position += 8;
    }

    @Override
    public void writeDouble(double value) {
        writeInt64(Double.doubleToRawLongBits(value));
    }

    @Override
    protected int writeCharacters(String str, boolean checkForNullCharacters) {
        int length = str.length();
        ensure(length + 1);
        byte[] bytes = buffer;
        int start = position;
        for (int i = 0; i < length; i++) {
            char c = str.charAt(i);
            if (c >= 0x80 || c == 0) {
                if (c == 0 && !checkForNullCharacters) {
                    bytes[start + i] = 0;
                    continue;
                }
                // Not ASCII, or a null character in a C string: the general encoding handles it
                return writeCharactersSlow(str, checkForNullCharacters, start);
            }
            bytes[start + i] = (byte) c;
        }
        bytes[start + length] = 0;
        position = start + length + 1;
        return length + 1;
    }

    private int writeCharactersSlow(String str, boolean checkForNullCharacters, int start) {
        position = start;
        try {
            return super.writeCharacters(str, checkForNullCharacters);
        } catch (BsonSerializationException e) {
            position = start;
            throw e;
        }
    }

    @Override
    public int getPosition() {
        return position;
    }

    @Override
    public int getSize() {
        // the buffer is written sequentially, so its size is its position
        return getPosition();
    }

    @Override
    public int pipe(OutputStream out) throws IOException {
        out.write(buffer, 0, position);
        return position;
    }

    @Override
    public byte[] toByteArray() {
        return Arrays.copyOf(buffer, position);
    }

    @Override
    public List<ByteBuf> getByteBuffers() {
        return List.of(new ByteBufNIO(ByteBuffer.wrap(buffer, 0, position).duplicate().order(ByteOrder.LITTLE_ENDIAN)));
    }

    @Override
    public void truncateToPosition(int newPosition) {
        if (newPosition > position || newPosition < 0) {
            throw new IllegalArgumentException();
        }
        position = newPosition;
    }

    private void ensure(int more) {
        int required = position + more;
        if (required > buffer.length) {
            buffer = Arrays.copyOf(buffer, Math.max(required, buffer.length << 1));
        }
    }

    private void checkPosition(int absolutePosition, int length) {
        if (absolutePosition < 0 || absolutePosition + length > position) {
            throw new IllegalArgumentException(String.format("position must be >= 0 and <= %d, but is %d", position - length, absolutePosition));
        }
    }
}
