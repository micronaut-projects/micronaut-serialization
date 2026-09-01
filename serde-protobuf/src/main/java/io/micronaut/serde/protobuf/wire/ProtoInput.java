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
        // single-byte varints cover tags, small ints, booleans and most lengths
        int p = position;
        if (p < buffer.length) {
            byte b = buffer[p];
            if (b >= 0) {
                position = p + 1;
                return b;
            }
        }
        return readVarint64MultiByte();
    }

    private long readVarint64MultiByte() throws IOException {
        long result = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            byte b = readByte();
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
        require(4);
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
        require(8);
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
        String result = new String(buffer, position, length, StandardCharsets.UTF_8);
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
        if (position + length > limit) {
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
        skip(0, wireType, limit);
    }

    /**
     * Skip a complete field value, including a deprecated group.
     *
     * @param fieldNumber The field number
     * @param wireType    The wire type
     * @param limit       The position the value must not extend past
     * @throws IOException If the payload is truncated or the group is malformed
     */
    public void skip(int fieldNumber, int wireType, int limit) throws IOException {
        switch (wireType) {
            case ProtoWire.VARINT -> readVarint64();
            case ProtoWire.FIXED64 -> {
                require(8);
                position += 8;
            }
            case ProtoWire.FIXED32 -> {
                require(4);
                position += 4;
            }
            case ProtoWire.LENGTH_DELIMITED -> {
                // read the length first: a compound assignment would capture the position from
                // before the length varint was consumed
                int length = readLength(limit);
                position += length;
            }
            case ProtoWire.START_GROUP -> skipGroup(fieldNumber, limit);
            case ProtoWire.END_GROUP -> throw new IOException("Malformed protobuf payload: unexpected end-group tag");
            default -> throw new IOException("Malformed protobuf payload: unsupported wire type " + wireType);
        }
    }

    private void skipGroup(int fieldNumber, int limit) throws IOException {
        while (position < limit) {
            int tag = readVarint32();
            int nestedFieldNumber = ProtoWire.fieldNumber(tag);
            int nestedWireType = ProtoWire.wireType(tag);
            if (nestedWireType == ProtoWire.END_GROUP) {
                if (nestedFieldNumber != fieldNumber) {
                    throw new IOException("Malformed protobuf payload: group " + fieldNumber
                        + " ended with field number " + nestedFieldNumber);
                }
                return;
            }
            skip(nestedFieldNumber, nestedWireType, limit);
        }
        throw new EOFException("Truncated protobuf group " + fieldNumber);
    }

    /**
     * Read one byte with no tag or length prefix.
     *
     * @return The byte
     * @throws IOException If the payload is truncated
     */
    public byte readRawByte() throws IOException {
        return readByte();
    }

    private byte readByte() throws IOException {
        if (position >= buffer.length) {
            throw new EOFException("Truncated protobuf payload");
        }
        return buffer[position++];
    }

    private void require(int bytes) throws IOException {
        if (position + bytes > buffer.length) {
            throw new EOFException("Truncated protobuf payload");
        }
    }
}
