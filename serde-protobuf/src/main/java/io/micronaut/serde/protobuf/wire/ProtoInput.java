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

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * A shared cursor over a Protocol Buffers payload.
 *
 * <p>Nested messages, packed runs and repeated fields all read from the same underlying bytes, so
 * they share one cursor and differ only in the limit they stop at.</p>
 *
 * @since 3.2
 */
@Internal
public final class ProtoInput {

    private static final int MAX_TAG_BYTES = 5;

    private final byte[] buffer;
    private int position;

    /**
     * Create a cursor over a complete payload.
     *
     * @param buffer The payload
     */
    public ProtoInput(byte[] buffer) {
        this.buffer = buffer;
    }

    /**
     * Returns the underlying payload.
     *
     * @return The payload
     */
    public byte[] buffer() {
        return buffer;
    }

    /**
     * Returns the current read position.
     *
     * @return The position
     */
    public int position() {
        return position;
    }

    /**
     * Move the read position.
     *
     * @param position The new position
     */
    public void position(int position) {
        this.position = position;
    }

    /**
     * Returns the total number of bytes available.
     *
     * @return The length
     */
    public int length() {
        return buffer.length;
    }

    /**
     * Read and validate a field tag.
     *
     * <p>A tag is a 32-bit value, so its varint is at most five bytes and may not be padded out
     * with redundant continuation bytes; a longer one hides a field number that does not fit.
     * Field number zero does not exist. Both are rejected rather than skipped, because a payload
     * containing either is malformed rather than merely unfamiliar.</p>
     *
     * @return The tag
     * @throws IOException If the tag is malformed or the payload is truncated
     */
    public int readTag() throws IOException {
        int start = position;
        long value = readVarint64();
        if (position - start > MAX_TAG_BYTES) {
            throw new IOException("Malformed protobuf payload: a field tag is longer than "
                + MAX_TAG_BYTES + " bytes");
        }
        if ((value & ~0xFFFFFFFFL) != 0) {
            throw new IOException("Malformed protobuf payload: a field tag does not fit in 32 bits");
        }
        int tag = (int) value;
        if (ProtoWire.fieldNumber(tag) == 0) {
            throw new IOException("Malformed protobuf payload: zero is not a legal field number");
        }
        return tag;
    }

    /**
     * Read a variable-width integer as a 32-bit value.
     *
     * @return The value
     * @throws IOException If the payload is truncated or malformed
     */
    public int readVarint32() throws IOException {
        return (int) readVarint64();
    }

    /**
     * Read a variable-width integer.
     *
     * @return The value
     * @throws IOException If the payload is truncated or malformed
     */
    public long readVarint64() throws IOException {
        return readVarint64(buffer.length);
    }

    /**
     * Read a variable-width integer without reading past an enclosing structure's limit.
     *
     * @param limit The position the value must not extend past
     * @return The value
     * @throws IOException If the payload is truncated or malformed
     */
    public long readVarint64(int limit) throws IOException {
        // single-byte varints cover tags, small ints, booleans and most lengths
        int p = position;
        if (p < limit) {
            byte b = buffer[p];
            if (b >= 0) {
                position = p + 1;
                return b;
            }
        }
        return readVarint64MultiByte(limit);
    }

    private long readVarint64MultiByte(int limit) throws IOException {
        long result = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            byte b = readByte(limit);
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
        }
        throw new IOException("Malformed protobuf payload: varint is longer than ten bytes");
    }

    /**
     * Read a fixed four-byte little-endian value.
     *
     * @return The value
     * @throws IOException If the payload is truncated
     */
    public int readFixed32() throws IOException {
        return readFixed32(buffer.length);
    }

    /**
     * Read a fixed four-byte little-endian value without reading past a limit.
     *
     * @param limit The position the value must not extend past
     * @return The value
     * @throws IOException If the payload is truncated
     */
    public int readFixed32(int limit) throws IOException {
        require(4, limit);
        int p = position;
        position = p + 4;
        return (buffer[p] & 0xFF)
            | ((buffer[p + 1] & 0xFF) << 8)
            | ((buffer[p + 2] & 0xFF) << 16)
            | ((buffer[p + 3] & 0xFF) << 24);
    }

    /**
     * Read a fixed eight-byte little-endian value.
     *
     * @return The value
     * @throws IOException If the payload is truncated
     */
    public long readFixed64() throws IOException {
        return readFixed64(buffer.length);
    }

    /**
     * Read a fixed eight-byte little-endian value without reading past a limit.
     *
     * @param limit The position the value must not extend past
     * @return The value
     * @throws IOException If the payload is truncated
     */
    public long readFixed64(int limit) throws IOException {
        require(8, limit);
        int p = position;
        position = p + 8;
        long result = 0;
        for (int i = 0; i < 8; i++) {
            result |= (long) (buffer[p + i] & 0xFF) << (8 * i);
        }
        return result;
    }

    /**
     * Read a length-prefixed byte array.
     *
     * @param limit The position the value must not extend past
     * @return The bytes
     * @throws IOException If the payload is truncated
     */
    public byte[] readBytes(int limit) throws IOException {
        int length = readLength(limit);
        require(length);
        byte[] result = new byte[length];
        System.arraycopy(buffer, position, result, 0, length);
        position += length;
        return result;
    }

    /**
     * Read a length-prefixed UTF-8 string.
     *
     * @param limit The position the value must not extend past
     * @return The string
     * @throws IOException If the payload is truncated
     */
    public String readString(int limit) throws IOException {
        int length = readLength(limit);
        require(length);
        String result = decodeUtf8(position, length);
        position += length;
        return result;
    }

    /**
     * Read a length prefix and validate it against the payload.
     *
     * @return The length
     * @throws IOException If the length is negative or exceeds the payload
     */
    public int readLength() throws IOException {
        return readLength(buffer.length);
    }

    /**
     * Read a length prefix and validate it against an enclosing structure's limit.
     *
     * <p>Checking against the limit rather than the whole payload is what stops a nested message
     * from claiming bytes that belong to its parent.</p>
     *
     * @param limit The position the value must not extend past
     * @return The length
     * @throws IOException If the length is negative or reaches past the limit
     */
    public int readLength(int limit) throws IOException {
        int length = readVarint32();
        if (length < 0) {
            throw new IOException("Malformed protobuf payload: negative length " + length);
        }
        // subtract rather than add: position + length overflows for a length near Integer.MAX_VALUE
        // and wraps negative, which would let the check pass
        if (length > limit - position) {
            throw new IOException("Malformed protobuf payload: a value of " + length
                + " bytes reaches past the end of its enclosing structure");
        }
        return length;
    }

    /**
     * Skip the value of a field with the given wire type.
     *
     * @param wireType The wire type
     * @throws IOException If the payload is truncated or the wire type is unknown
     */
    public void skip(int wireType) throws IOException {
        skip(wireType, buffer.length);
    }

    /**
     * Skip the value of a field with the given wire type, without reading past a limit.
     *
     * @param wireType The wire type
     * @param limit    The position the value must not extend past
     * @throws IOException If the payload is truncated or the wire type is unknown
     */
    public void skip(int wireType, int limit) throws IOException {
        switch (wireType) {
            case ProtoWire.VARINT -> readVarint64(limit);
            case ProtoWire.FIXED64 -> {
                require(8, limit);
                position += 8;
            }
            case ProtoWire.FIXED32 -> {
                require(4, limit);
                position += 4;
            }
            case ProtoWire.LENGTH_DELIMITED -> {
                // read the length first: a compound assignment would capture the position from
                // before the length varint was consumed
                int length = readLength(limit);
                position += length;
            }
            // groups are a proto2-only encoding this backend does not support. Skipping them meant
            // recursing once per nested start-group tag, and a start-group tag is a single byte, so
            // a small payload could exhaust the stack. Rejecting them removes the recursion instead
            // of bounding it, and stops group-internal tags bypassing readTag validation.
            case ProtoWire.START_GROUP, ProtoWire.END_GROUP ->
                throw new IOException("Malformed protobuf payload: group encoding is not supported");
            default -> throw new IOException("Malformed protobuf payload: unsupported wire type " + wireType);
        }
    }

    /**
     * Read one byte with no tag or length prefix.
     *
     * @return The byte
     * @throws IOException If the payload is truncated
     */
    public byte readRawByte() throws IOException {
        return readByte(buffer.length);
    }

    /**
     * Decode UTF-8, rejecting malformed input.
     *
     * <p>The {@link String} constructor substitutes U+FFFD for an invalid sequence, which silently
     * corrupts the value and disagrees with the reference implementation. Protobuf requires
     * {@code string} fields to be valid UTF-8, so an invalid one is a malformed payload.</p>
     *
     * <p>ASCII is always valid UTF-8 and is the common case, so it keeps the fast constructor;
     * only a payload with a high bit set pays for the checking decoder.</p>
     */
    private String decodeUtf8(int offset, int length) throws IOException {
        int end = offset + length;
        for (int i = offset; i < end; i++) {
            if (buffer[i] < 0) {
                return decodeUtf8Checked(offset, length);
            }
        }
        return new String(buffer, offset, length, StandardCharsets.UTF_8);
    }

    private String decodeUtf8Checked(int offset, int length) throws IOException {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(buffer, offset, length)).toString();
        } catch (CharacterCodingException e) {
            throw new IOException("Malformed protobuf payload: a string field is not valid UTF-8", e);
        }
    }

    private byte readByte(int limit) throws IOException {
        if (position >= limit) {
            throw new EOFException("Truncated protobuf payload");
        }
        return buffer[position++];
    }

    private void require(int bytes) throws IOException {
        require(bytes, buffer.length);
    }

    private void require(int bytes, int limit) throws IOException {
        // subtract rather than add, for the same overflow reason as readLength
        if (bytes > limit - position) {
            throw new EOFException("Truncated protobuf payload");
        }
    }
}
