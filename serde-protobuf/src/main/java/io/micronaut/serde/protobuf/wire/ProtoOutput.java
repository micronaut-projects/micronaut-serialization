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
package io.micronaut.serde.protobuf.wire;

import io.micronaut.core.annotation.Internal;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * A growable byte sink for Protocol Buffers output.
 *
 * <p>Nested messages are length-prefixed, so their length has to be known before their payload is
 * written. Each nested structure therefore writes into its own {@code ProtoOutput}, which is copied
 * into the parent once its length is known. Instances are pooled by the encoder and reused through
 * {@link #reset()}.</p>
 *
 * @since 3.2
 */
@Internal
public final class ProtoOutput {

    private static final byte UTF8_REPLACEMENT = (byte) '?';

    private byte[] buffer;
    private int size;

    /**
     * Create an output with a default initial capacity.
     */
    public ProtoOutput() {
        this(64);
    }

    /**
     * Create an output with the given initial capacity.
     *
     * @param initialCapacity The initial capacity in bytes
     */
    public ProtoOutput(int initialCapacity) {
        this.buffer = new byte[initialCapacity];
    }

    /**
     * Returns the number of bytes written so far.
     *
     * @return The size in bytes
     */
    public int size() {
        return size;
    }

    /**
     * Discard the written bytes but keep the allocated array, so the instance can be reused.
     */
    public void reset() {
        size = 0;
    }

    /**
     * Write a field tag.
     *
     * @param tag The pre-combined tag, from {@link ProtoWire#tag(int, int)}
     */
    public void writeTag(int tag) {
        writeVarint(Integer.toUnsignedLong(tag));
    }

    /**
     * Write an unsigned variable-width integer.
     *
     * @param value The value, interpreted as unsigned
     */
    public void writeVarint(long value) {
        int s = size;
        if ((value & ~0x7FL) == 0 && s < buffer.length) {
            // single-byte varints cover tags, small ints, booleans and most lengths
            buffer[s] = (byte) value;
            size = s + 1;
            return;
        }
        writeVarintMultiByte(value);
    }

    /**
     * Write an {@code int32}. Negative values are sign-extended to 64 bits and therefore always
     * occupy ten bytes, matching the reference implementation.
     *
     * @param value The value
     */
    public void writeInt32(int value) {
        writeVarint(value >= 0 ? value : (long) value);
    }

    /**
     * Write a fixed four-byte little-endian value.
     *
     * @param value The value
     */
    public void writeFixed32(int value) {
        ensure(4);
        byte[] b = buffer;
        int s = size;
        b[s] = (byte) value;
        b[s + 1] = (byte) (value >>> 8);
        b[s + 2] = (byte) (value >>> 16);
        b[s + 3] = (byte) (value >>> 24);
        size = s + 4;
    }

    /**
     * Write a fixed eight-byte little-endian value.
     *
     * @param value The value
     */
    public void writeFixed64(long value) {
        ensure(8);
        byte[] b = buffer;
        int s = size;
        for (int i = 0; i < 8; i++) {
            b[s + i] = (byte) (value >>> (8 * i));
        }
        size = s + 8;
    }

    /**
     * Write a single byte with no tag or length prefix.
     *
     * @param value The byte
     */
    public void writeByte(byte value) {
        ensure(1);
        buffer[size++] = value;
    }

    /**
     * Write raw bytes with no length prefix.
     *
     * @param bytes  The bytes
     * @param offset The offset
     * @param length The length
     */
    public void writeRaw(byte[] bytes, int offset, int length) {
        ensure(length);
        System.arraycopy(bytes, offset, buffer, size, length);
        size += length;
    }

    /**
     * Write bytes prefixed with their length.
     *
     * @param bytes The bytes
     */
    public void writeLengthDelimited(byte[] bytes) {
        writeVarint(bytes.length);
        writeRaw(bytes, 0, bytes.length);
    }

    /**
     * Write a UTF-8 string prefixed with its byte length.
     *
     * <p>The string is measured and then encoded straight into the buffer, so no intermediate byte
     * array is allocated. Unpaired surrogates are replaced with {@code '?'}, which is what
     * {@link String#getBytes(java.nio.charset.Charset)} does for UTF-8.</p>
     *
     * @param value The string
     */
    public void writeString(String value) {
        int length = value.length();
        int byteLength = utf8Length(value, length);
        writeVarint(byteLength);
        ensure(byteLength);
        byte[] b = buffer;
        int s = size;
        int i = 0;
        while (i < length) {
            char c = value.charAt(i++);
            if (c < 0x80) {
                b[s++] = (byte) c;
            } else if (c < 0x800) {
                b[s++] = (byte) (0xC0 | (c >> 6));
                b[s++] = (byte) (0x80 | (c & 0x3F));
            } else if (Character.isSurrogate(c)) {
                char low = i < length ? value.charAt(i) : 0;
                if (Character.isHighSurrogate(c) && Character.isLowSurrogate(low)) {
                    int codePoint = Character.toCodePoint(c, low);
                    b[s++] = (byte) (0xF0 | (codePoint >> 18));
                    b[s++] = (byte) (0x80 | ((codePoint >> 12) & 0x3F));
                    b[s++] = (byte) (0x80 | ((codePoint >> 6) & 0x3F));
                    b[s++] = (byte) (0x80 | (codePoint & 0x3F));
                    // the low surrogate is part of this code point
                    i++;
                } else {
                    b[s++] = UTF8_REPLACEMENT;
                }
            } else {
                b[s++] = (byte) (0xE0 | (c >> 12));
                b[s++] = (byte) (0x80 | ((c >> 6) & 0x3F));
                b[s++] = (byte) (0x80 | (c & 0x3F));
            }
        }
        size = s;
    }

    /**
     * Append the contents of another output, prefixed with its length.
     *
     * @param other The other output
     */
    public void writeLengthDelimited(ProtoOutput other) {
        writeVarint(other.size);
        writeRaw(other.buffer, 0, other.size);
    }

    /**
     * Append the contents of another output verbatim.
     *
     * @param other The other output
     */
    public void writeRaw(ProtoOutput other) {
        writeRaw(other.buffer, 0, other.size);
    }

    /**
     * Returns a copy of the written bytes.
     *
     * @return The bytes
     */
    public byte[] toByteArray() {
        return Arrays.copyOf(buffer, size);
    }

    /**
     * Write the accumulated bytes to a stream.
     *
     * @param outputStream The stream
     * @throws IOException If writing fails
     */
    public void writeTo(OutputStream outputStream) throws IOException {
        outputStream.write(buffer, 0, size);
    }

    private static int utf8Length(String value, int length) {
        int bytes = length;
        int i = 0;
        while (i < length) {
            char c = value.charAt(i++);
            if (c < 0x800) {
                // one byte below 0x80, two below 0x800; the base count already covers the first
                bytes += c < 0x80 ? 0 : 1;
            } else if (Character.isSurrogate(c)) {
                if (Character.isHighSurrogate(c) && i < length && Character.isLowSurrogate(value.charAt(i))) {
                    // a surrogate pair is two chars and four bytes
                    bytes += 2;
                    i++;
                }
                // an unpaired surrogate becomes a single replacement byte
            } else {
                bytes += 2;
            }
        }
        return bytes;
    }

    private void writeVarintMultiByte(long value) {
        ensure(10);
        byte[] b = buffer;
        int s = size;
        while ((value & ~0x7FL) != 0) {
            b[s++] = (byte) ((((int) value) & 0x7F) | 0x80);
            value >>>= 7;
        }
        b[s++] = (byte) value;
        size = s;
    }

    private void ensure(int additional) {
        int required = size + additional;
        if (required > buffer.length) {
            buffer = Arrays.copyOf(buffer, Math.max(buffer.length << 1, required));
        }
    }
}
