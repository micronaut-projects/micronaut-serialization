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
package io.micronaut.serde.protobuf.annotation;

/**
 * The Protocol Buffers scalar types that can be requested for a property.
 *
 * <p>A single Java type maps to several possible wire representations. A Java {@code int} is
 * {@code int32} by default, but {@code sint32} encodes negative values in a fifth of the bytes,
 * and {@code fixed32} is cheaper for values that are usually large.</p>
 *
 * <p>This is prototype API and subject to change.</p>
 *
 * @since 3.2
 */
public enum ProtoType {

    /**
     * Derive the wire representation from the Java type.
     */
    DEFAULT,

    /**
     * Variable-width signed 32-bit. Negative values always occupy ten bytes.
     */
    INT32,

    /**
     * Variable-width 32-bit with zig-zag encoding. Efficient for negative values.
     */
    SINT32,

    /**
     * Variable-width unsigned 32-bit.
     */
    UINT32,

    /**
     * Fixed four-byte unsigned 32-bit.
     */
    FIXED32,

    /**
     * Fixed four-byte signed 32-bit.
     */
    SFIXED32,

    /**
     * Variable-width signed 64-bit. Negative values always occupy ten bytes.
     */
    INT64,

    /**
     * Variable-width 64-bit with zig-zag encoding. Efficient for negative values.
     */
    SINT64,

    /**
     * Variable-width unsigned 64-bit.
     */
    UINT64,

    /**
     * Fixed eight-byte unsigned 64-bit.
     */
    FIXED64,

    /**
     * Fixed eight-byte signed 64-bit.
     */
    SFIXED64
}
