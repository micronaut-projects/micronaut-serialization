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
package io.micronaut.serde.protobuf;

import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.protobuf.annotation.ProtoType;

/**
 * One property of a message, with everything the encoder and decoder need already computed.
 *
 * <p>Field tags and the choice between varint, zig-zag and fixed-width encoding follow from the
 * declared {@link ProtoType}, which never changes. Resolving them once here keeps the per-value
 * path down to an integer switch and a write, with no annotation or enum lookups.</p>
 *
 * @param name            The serde property name
 * @param number          The protobuf field number
 * @param type            The declared wire representation
 * @param packableElement Whether the element type is a scalar that protobuf packs by default
 * @param bytesField      Whether the property is a {@code bytes} field rather than a repeated one
 * @param varintTag       The pre-combined tag for a varint value
 * @param fixed32Tag      The pre-combined tag for a four-byte value
 * @param fixed64Tag      The pre-combined tag for an eight-byte value
 * @param lengthTag       The pre-combined tag for a length-delimited value
 * @param intKind         How {@code int}-width values are encoded, one of the {@code KIND_} constants
 * @param longKind        How {@code long}-width values are encoded, one of the {@code KIND_} constants
 * @since 3.2
 */
@Internal
public record ProtoProperty(
    String name,
    int number,
    ProtoType type,
    boolean packableElement,
    boolean bytesField,
    int varintTag,
    int fixed32Tag,
    int fixed64Tag,
    int lengthTag,
    int intKind,
    int longKind
) {

    /**
     * A plain varint, sign-extending negative values to ten bytes.
     */
    public static final int KIND_INT32 = 0;

    /**
     * A plain varint over the full 64-bit value.
     */
    public static final int KIND_INT64 = 1;

    /**
     * A varint over the zig-zag encoding, so small negative values stay small.
     */
    public static final int KIND_ZIGZAG = 2;

    /**
     * A varint over the unsigned 32-bit value.
     */
    public static final int KIND_UINT32 = 3;

    /**
     * A fixed four-byte value.
     */
    public static final int KIND_FIXED32 = 4;

    /**
     * A fixed eight-byte value.
     */
    public static final int KIND_FIXED64 = 5;

    /**
     * The wire type a value of the given kind travels under.
     *
     * @param kind One of the {@code KIND_} constants
     * @return The wire type
     */
    public static int wireTypeOf(int kind) {
        return switch (kind) {
            case KIND_FIXED32 -> io.micronaut.serde.protobuf.wire.ProtoWire.FIXED32;
            case KIND_FIXED64 -> io.micronaut.serde.protobuf.wire.ProtoWire.FIXED64;
            default -> io.micronaut.serde.protobuf.wire.ProtoWire.VARINT;
        };
    }

    /**
     * The pre-combined tag for a value of the given kind.
     *
     * @param kind One of the {@code KIND_} constants
     * @return The tag
     */
    public int tagFor(int kind) {
        return switch (kind) {
            case KIND_FIXED32 -> fixed32Tag;
            case KIND_FIXED64 -> fixed64Tag;
            default -> varintTag;
        };
    }
}
