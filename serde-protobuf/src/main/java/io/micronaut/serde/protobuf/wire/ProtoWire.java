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

/**
 * Protocol Buffers wire type constants and the tag/zig-zag arithmetic shared by the reader
 * and the writer.
 *
 * @since 3.2
 */
@Internal
public final class ProtoWire {

    /**
     * Variable-width integer.
     */
    public static final int VARINT = 0;

    /**
     * Fixed eight-byte value.
     */
    public static final int FIXED64 = 1;

    /**
     * Length-prefixed bytes: strings, byte arrays, nested messages and packed repeated fields.
     */
    public static final int LENGTH_DELIMITED = 2;

    /**
     * Fixed four-byte value.
     */
    public static final int FIXED32 = 5;

    private ProtoWire() {
    }

    /**
     * Combine a field number and wire type into a tag.
     *
     * @param fieldNumber The field number
     * @param wireType    The wire type
     * @return The tag
     */
    public static int tag(int fieldNumber, int wireType) {
        return (fieldNumber << 3) | wireType;
    }

    /**
     * Extract the field number from a tag.
     *
     * @param tag The tag
     * @return The field number
     */
    public static int fieldNumber(int tag) {
        return tag >>> 3;
    }

    /**
     * Extract the wire type from a tag.
     *
     * @param tag The tag
     * @return The wire type
     */
    public static int wireType(int tag) {
        return tag & 7;
    }

    /**
     * Zig-zag encode a 32-bit value so that small magnitudes map to small varints.
     *
     * @param value The value
     * @return The zig-zag encoded value
     */
    public static int zigZagEncode32(int value) {
        return (value << 1) ^ (value >> 31);
    }

    /**
     * Reverse {@link #zigZagEncode32(int)}.
     *
     * @param value The zig-zag encoded value
     * @return The value
     */
    public static int zigZagDecode32(int value) {
        return (value >>> 1) ^ -(value & 1);
    }

    /**
     * Zig-zag encode a 64-bit value so that small magnitudes map to small varints.
     *
     * @param value The value
     * @return The zig-zag encoded value
     */
    public static long zigZagEncode64(long value) {
        return (value << 1) ^ (value >> 63);
    }

    /**
     * Reverse {@link #zigZagEncode64(long)}.
     *
     * @param value The zig-zag encoded value
     * @return The value
     */
    public static long zigZagDecode64(long value) {
        return (value >>> 1) ^ -(value & 1);
    }
}
